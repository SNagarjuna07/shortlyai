package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;
import com.shortlyai.url.common.exception.DuplicateSlugException;
import com.shortlyai.url.common.exception.UrlNotFoundException;
import com.shortlyai.url.dlq.FailedEventService;
import com.shortlyai.url.events.UrlClickedEvent;
import com.shortlyai.url.events.UrlCreatedEvent;
import com.shortlyai.url.events.UrlDeletedEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ShorteningServiceImpl implements ShorteningService {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final FailedEventService failedEventService;

    private final Executor clickTrackingExecutor;

    private final String baseDomain;

    private final String apiPrefix;

    private final long defaultExpiryDays;

    private final long cacheTtlSeconds;

    private final String urlCreatedTopic;

    private final String urlClickedTopic;

    private final String urlDeletedTopic;

    public ShorteningServiceImpl(
            UrlRepository urlRepository,
            StringRedisTemplate stringRedisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${url.base-domain}") String baseDomain,
            @Value("${api.prefix}") String apiPrefix,
            @Value("${url.default-expiry-days}") long defaultExpiryDays,
            @Value("${url.cache-ttl-seconds}") long cacheTtlSeconds,
            @Value("${spring.kafka.topics.url-created}") String urlCreatedTopic,
            @Value("${spring.kafka.topics.url-clicked}") String urlClickedTopic,
            @Value("${spring.kafka.topics.url-deleted}") String urlDeletedTopic,
            FailedEventService failedEventService,
            Executor clickTrackingExecutor) {

        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.failedEventService = failedEventService;
        this.clickTrackingExecutor = clickTrackingExecutor;
        this.baseDomain = baseDomain;
        this.apiPrefix = apiPrefix;
        this.defaultExpiryDays = defaultExpiryDays;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.urlCreatedTopic = urlCreatedTopic;
        this.urlClickedTopic = urlClickedTopic;
        this.urlDeletedTopic = urlDeletedTopic;
    }

    private static final String CACHE_PREFIX = "url:";

    // \u0000 (null byte)
    // URL is stored LAST so split("\u0000", 4) never cuts into it.
    // Format: "urlId\u0000userId\u0000expiresAtEpochMs\u0000originalUrl"
    private static final String CACHE_SEP = "\u0000";

    private static final String PENDING_CLICKS_PREFIX = "clicks:pending:";

    @Override
    public ShortenResponse shorten(ShortenRequest request, UUID userId) {

        log.info("URL shortening request for userId: {}", userId);

        // Check whether user requested a custom slug
        boolean isCustom =
                request.customSlug() != null &&
                        !request.customSlug().isBlank();

        // Ensure custom slug is unique
        if (isCustom && urlRepository.existsBySlug(request.customSlug())) {
            throw new DuplicateSlugException("This slug is already taken");
        }

        // Determine slug (custom or temporary)
        String slug = isCustom
                ? request.customSlug()
                : "tmp" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        // Build URL entity
        Url url = Url.builder()
                .originalUrl(request.originalUrl())
                .userId(userId)
                .isCustom(isCustom)
                .expiresAt(
                        Instant.now().plus(
                                request.expiryDays() == null
                                        ? defaultExpiryDays
                                        : request.expiryDays(),
                                ChronoUnit.DAYS
                        )
                )
                .slug(slug)
                .build();

        // First save to generate database ID
        Url savedUrl = urlRepository.save(url);

        // Generate a random (non-sequential) slug if custom slug was not provided.
        if (!isCustom) {

            String generatedSlug;
            int attempts = 0;

            do {
                generatedSlug = Base62.generateRandomSlug();
                attempts++;

                if (attempts > 5) {
                    throw new IllegalStateException(
                            "Failed to generate unique slug after " + attempts + " attempts");
                }

            } while (urlRepository.existsBySlug(generatedSlug));

            savedUrl.setSlug(generatedSlug);

            savedUrl = urlRepository.save(savedUrl);
        }

        long expiresAtMs = savedUrl.getExpiresAt() == null
                ? Long.MAX_VALUE
                : savedUrl.getExpiresAt().toEpochMilli();

        stringRedisTemplate.opsForValue().set(
                CACHE_PREFIX + savedUrl.getSlug(),
                savedUrl.getId() +
                        CACHE_SEP +
                        savedUrl.getUserId() +
                        CACHE_SEP +
                        expiresAtMs +
                        CACHE_SEP +
                        savedUrl.getOriginalUrl(),
                Duration.ofSeconds(cacheTtlSeconds)
        );

        // Publish Kafka event
        publishCreatedEvent(savedUrl);

        log.info(
                "Created short-url '{}' for userId: {}",
                savedUrl.getSlug(),
                userId
        );

        return mapToResponse(savedUrl);
    }

    @Override
    public String resolve(String slug, HttpServletRequest request) {

        String ipHash = sha256(request.getRemoteAddr());
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        String cached = stringRedisTemplate.opsForValue()
                .get(CACHE_PREFIX + slug);

        // cache hit
        if (cached != null) {

            String[] parts = cached.split(CACHE_SEP, 4);

            if (parts.length < 4) {

                // stale pre-migration entry (old 3-part format) - evict it and
                // fall through to the cache-miss path below instead of returning,
                // so this request still gets served correctly from the DB
                log.warn("Stale cache format for slug '{}', evicting and falling back to DB", slug);

                stringRedisTemplate.delete(CACHE_PREFIX + slug);

            } else {

                log.debug("Cache hit for slug: {}", slug);

                Long urlId = Long.parseLong(parts[0]);

                UUID userId = UUID.fromString(parts[1]);

                long expiresAtMs = Long.parseLong(parts[2]);

                String originalUrl = parts[3];

                if (Instant.ofEpochMilli(expiresAtMs).isBefore(Instant.now())) {

                    log.info("Cached slug '{}' has expired, evicting and treating as not found", slug);

                    stringRedisTemplate.delete(CACHE_PREFIX + slug);

                    throw new UrlNotFoundException("URL not found");
                }

                // Fire-and-forget: Kafka publish + click-count UPDATE happen off this thread
                dispatchClickTracking(urlId, slug, userId, ipHash, userAgent, referer);

                return originalUrl;   // only returns here - the valid, non-stale, non-expired case
            }
        }

        log.debug("Cache miss for slug. Fetching from DB: {}", slug);

        // cache miss
        Url shortUrl = urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(
                        slug,
                        Instant.now()
                )
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        long expiresAtMs = shortUrl.getExpiresAt() == null
                ? Long.MAX_VALUE
                : shortUrl.getExpiresAt().toEpochMilli();

        stringRedisTemplate.opsForValue().set(
                CACHE_PREFIX + slug,
                shortUrl.getId() +
                        CACHE_SEP +
                        shortUrl.getUserId() +
                        CACHE_SEP +
                        expiresAtMs +
                        CACHE_SEP +
                        shortUrl.getOriginalUrl(),
                Duration.ofSeconds(cacheTtlSeconds)
        );

        dispatchClickTracking(
                shortUrl.getId(),
                slug,
                shortUrl.getUserId(),
                ipHash,
                userAgent,
                referer
        );

        return shortUrl.getOriginalUrl();
    }

    // Runs on clickTrackingExecutor (virtual-thread-per-task), never on the request thread
    private void dispatchClickTracking(
            Long urlId,
            String slug,
            UUID ownerId,
            String ipHash,
            String userAgent,
            String referer
    ) {

        clickTrackingExecutor.execute(() -> {

            try {

                publishClickEvent(urlId, slug, ownerId, ipHash, userAgent, referer);

                stringRedisTemplate.opsForValue()
                        .increment(PENDING_CLICKS_PREFIX + urlId);

            } catch (Exception e) {

                log.error("Async click tracking failed for slug '{}' urlId {}: {}",
                        slug, urlId, e.getMessage());
            }
        });
    }

    @Override
    @Transactional
    public void delete(Long id, UUID userId) {

        // fetch URL
        Url url = urlRepository.findByIdAndUserIdAndIsActiveTrue(id, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        // fetch noOfRows
        int rowsDeleted = urlRepository.softDeleteByIdAndUserId(id, userId, Instant.now());

        // if no rows deleted, throw
        if (rowsDeleted == 0) {
            throw new UrlNotFoundException("URL not found");
        }

        log.info("Soft-deleted URL slug: {} by userId: {}", url.getSlug(), userId);

        // cache evict
        stringRedisTemplate.delete(CACHE_PREFIX + url.getSlug());

        // publish Kafka - async
        publishDeletedEvent(url);
    }

    @Override
    @Transactional(readOnly = true)
    public ShortenResponse getUrl(Long id, UUID userId) {

        Url url = urlRepository.findByIdAndUserIdAndIsActiveTrue(id, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        return mapToResponse(url);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShortenResponse> getUserUrls(UUID userId, Pageable pageable) {

        return urlRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShortenResponse getUrlBySlug(String slug, UUID userId) {

        Url url = urlRepository
                .findBySlugAndUserIdAndIsActiveTrue(slug, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found with slug: " + slug)
                );

        return mapToResponse(url);
    }

    @Transactional
    public void deleteUrl(String slug, UUID userId) {

        // fetch - throws UrlNotFoundException if not found (GlobalExceptionHandler -> 404)
        Url url = urlRepository.findBySlugAndUserIdAndIsActiveTrue(slug, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL slug " + slug + " not found"));

        // Soft-delete, same as delete(id, userId). Never hard-delete here:
        // analytics-service keeps click history keyed off this row, and a
        // hard delete would leave dangling references / break click stats.
        int rowsDeleted = urlRepository.softDeleteBySlugAndUserId(slug, userId, Instant.now());

        if (rowsDeleted == 0) {
            throw new UrlNotFoundException("URL slug " + slug + " not found");
        }

        // evict Redis cache
        String cacheKey = CACHE_PREFIX + slug;

        stringRedisTemplate.delete(cacheKey);

        // publish url.deleted event (Kafka)
        publishDeletedEvent(url);

        log.info("Soft-deleted URL slug: {} urlId: {} userId: {}", slug, url.getId(), userId);
    }

    private ShortenResponse mapToResponse(Url u) {

        return new ShortenResponse(
                u.getId(),
                u.getSlug(),
                baseDomain + apiPrefix + "/r/" + u.getSlug(),
                u.getOriginalUrl(),
                u.getUserId(),
                u.isCustom(),
                u.getClickCount(),
                u.getExpiresAt(),
                u.getCreatedAt()
        );
    }

    private void publishCreatedEvent(Url savedUrl) {

        String slug = savedUrl.getSlug();
        Long urlId = savedUrl.getId();

        // Build event once - reuse in both send and DLQ save (if needed)
        UrlCreatedEvent event = new UrlCreatedEvent(
                urlId, slug, savedUrl.getOriginalUrl(),
                baseDomain + "/" + slug, savedUrl.getUserId(),
                savedUrl.getExpiresAt(), savedUrl.getCreatedAt()
        );

        try {
            kafkaTemplate.send(urlCreatedTopic, slug, event)
                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            log.error("Kafka publish failed: topic: {} slug: {} error: {}",
                                    urlCreatedTopic, slug, ex.getMessage());

                            failedEventService.save(urlCreatedTopic, slug, event, ex.getMessage());

                        } else {

                            log.debug("Published url.created: topic: {} slug: {} urlId: {} partition: {}",
                                    urlCreatedTopic, slug, urlId,
                                    result.getRecordMetadata().partition());
                        }
                    });
        } catch (Exception ex) {

            log.error("Kafka send failed immediately", ex);

            failedEventService.save(
                    urlCreatedTopic,
                    slug,
                    event,
                    ex.getMessage()
            );
        }
    }

    private void publishDeletedEvent(Url url) {

        String slug = url.getSlug();
        Long urlId = url.getId();

        UrlDeletedEvent event = new UrlDeletedEvent(urlId, slug, url.getUserId(), Instant.now());

        try {

            kafkaTemplate.send(urlDeletedTopic, slug, event)
                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            log.error("Kafka publish failed: topic: {} slug: {} error: {}",
                                    urlDeletedTopic, slug, ex.getMessage());

                            failedEventService.save(urlDeletedTopic, slug, event, ex.getMessage());

                        } else {

                            log.debug("Published url.deleted: topic: {} slug: {} urlId: {} partition: {}",
                                    urlDeletedTopic, slug, urlId,
                                    result.getRecordMetadata().partition());
                        }
                    });

        } catch (Exception e) {

            log.error("Failed to publish " + urlDeletedTopic + " event: ", e);

            failedEventService.save(
                    urlDeletedTopic,
                    slug,
                    event,
                    e.getMessage()
            );
        }
    }

    private void publishClickEvent(
            Long urlId,
            String slug,
            UUID ownerId,
            String ipHash,
            String userAgent,
            String referer
    ) {

        UrlClickedEvent event = new UrlClickedEvent(
                urlId,
                slug,
                userAgent,
                ipHash,
                referer,
                null, // country - not resolved at url-service layer
                null,            // city - not resolved at url-service layer
                Instant.now(),
                ownerId
        );

        try {

            kafkaTemplate.send(urlClickedTopic, slug, event)
                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            log.error("Kafka publish failed: topic: {} slug: {} error: {}",
                                    urlClickedTopic, slug, ex.getMessage());

                            failedEventService.save(urlClickedTopic, slug, event, ex.getMessage());

                        } else {

                            log.debug("Published url.clicked: topic: {} slug: {} urlId: {} partition: {}",
                                    urlClickedTopic, slug, urlId,
                                    result.getRecordMetadata().partition());
                        }
                    });

        } catch (Exception e) {

            log.error("Failed to publish " + urlClickedTopic + " event: ", e);

            failedEventService.save(
                    urlClickedTopic,
                    slug,
                    event,
                    e.getMessage()
            );
        }
    }

    private String sha256(String input) {

        try {

            var digest = java.security.MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            var hex = new StringBuilder();

            for (byte b : hash) hex.append(String.format("%02x", b));

            return hex.toString();

        } catch (Exception _) {
            return "unknown";
        }
    }
}