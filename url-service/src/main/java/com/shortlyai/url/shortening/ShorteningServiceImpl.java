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

@Service
@Transactional
@Slf4j
public class ShorteningServiceImpl implements ShorteningService {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final FailedEventService failedEventService;

    private final String baseDomain;

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
            @Value("${url.default-expiry-days}") long defaultExpiryDays,
            @Value("${url.cache-ttl-seconds}") long cacheTtlSeconds,
            @Value("${spring.kafka.topics.url-created}") String urlCreatedTopic,
            @Value("${spring.kafka.topics.url-clicked}") String urlClickedTopic,
            @Value("${spring.kafka.topics.url-deleted}") String urlDeletedTopic, FailedEventService failedEventService) {

        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.failedEventService = failedEventService;
        this.baseDomain = baseDomain;
        this.defaultExpiryDays = defaultExpiryDays;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.urlCreatedTopic = urlCreatedTopic;
        this.urlClickedTopic = urlClickedTopic;
        this.urlDeletedTopic = urlDeletedTopic;
    }

    // Used by REDIS for caching
    private static final String CACHE_PREFIX = "url:";

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

        // Build URL entity
        Url url = new Url();
        url.setOriginalUrl(request.originalUrl());
        url.setUserId(userId);
        url.setCustom(isCustom);

        // Set expiry date, default if null
        url.setExpiresAt(
                Instant.now().plus(
                        request.expiryDays() == null
                                ? defaultExpiryDays
                                : request.expiryDays(),
                        ChronoUnit.DAYS
                )
        );

        // Set custom slug if provided else temp slug so DataIntegrityViolationException does not occur
        if (isCustom) {
            url.setSlug(request.customSlug());

        } else {

            url.setSlug("tmp" + java.util.UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
            );
        }

        // First save to generate database ID
        Url savedUrl = urlRepository.save(url);

        // Generate Base62 slug from ID if custom slug was not provided
        if (!isCustom) {

            savedUrl.setSlug(Base62.encode(savedUrl.getId()));

            savedUrl = urlRepository.save(savedUrl);
        }

        // Cache slug -> original URL in Redis
        stringRedisTemplate.opsForValue().set(
                CACHE_PREFIX + savedUrl.getSlug(),
                savedUrl.getId() +
                        "|" +
                        savedUrl.getOriginalUrl() +
                        "|" +
                        savedUrl.getUserId(), // <- urlId|url|userId
                Duration.ofSeconds(cacheTtlSeconds)
        );

        // Publish kafka event - async because of CompletableFuture
        publishCreatedEvent(savedUrl);

        log.info(
                "Created short-url '{}' for userId: {}",
                savedUrl.getSlug(),
                userId
        );

        // Return response DTO
        return mapToResponse(savedUrl);
    }

    @Override
    @Transactional()
    public String resolve(String slug, HttpServletRequest request) {

        String cached = stringRedisTemplate.opsForValue()
                .get(CACHE_PREFIX + slug);

        // cache hit
        if (cached != null) {

            log.info("Cache hit for slug: {}", slug);

            // parse the url which contains id, if Redis is hit there will no id
            String[] parts = cached.split("\\|", 3); // \\| splits 1st |

            Long urlId = Long.parseLong(parts[0]);

            String originalUrl = parts[1];

            UUID userId = UUID.fromString(parts[2]);

            // publish click also on cache hit
            publishClickEvent(urlId, slug, userId, request);

            // Increment click counter in DB
            urlRepository.incrementClickCount(urlId);

            return originalUrl;
        }

        log.info("Cache miss for slug. Fetching from DB: {}", slug);

        // cache miss
        Url shortUrl = urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(
                        slug,
                        Instant.now()
                )
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        // save to REDIS
        stringRedisTemplate.opsForValue().set(
                CACHE_PREFIX + slug,
                shortUrl.getId() +
                        "|" +
                        shortUrl.getOriginalUrl() +
                        "|" +
                        shortUrl.getUserId(), // <- same format
                Duration.ofSeconds(cacheTtlSeconds)
        );

        // publish Kafka async
        publishClickEvent(shortUrl.getId(), slug, shortUrl.getUserId(), request);

        // Increment the click count in DB
        urlRepository.incrementClickCount(shortUrl.getId());

        return shortUrl.getOriginalUrl();
    }

    @Override
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
                .findBySlugAndUserId(slug, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found with slug: " + slug)
                );

        return mapToResponse(url);
    }

    @Transactional
    public void deleteUrl(String slug, UUID userId) {

        // fetch - throws UrlNotFoundException if not found (GlobalExceptionHandler -> 404)
        Url url = urlRepository.findBySlugAndUserId(slug, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL slug " + slug + " not found"));

        // delete from Postgres
        urlRepository.delete(url);

        // evict Redis cache
        String cacheKey = "url:" + slug;

        stringRedisTemplate.delete(cacheKey);

        // publish url.deleted event (Kafka)
        publishDeletedEvent(url);

        log.info("Deleted URL slug={} urlId={} userId={}", slug, url.getId(), userId);
    }

    private ShortenResponse mapToResponse(Url u) {

        return new ShortenResponse(
                u.getId(),
                u.getSlug(),
                baseDomain + "/" + u.getSlug(),
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
                            log.error("Kafka publish failed: topic={} slug={} error={}",
                                    urlCreatedTopic, slug, ex.getMessage());
                            failedEventService.save(urlCreatedTopic, slug, event, ex.getMessage());
                        } else {
                            log.debug("Published url.created: topic={} slug={} urlId={} partition={}",
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

        kafkaTemplate.send(urlDeletedTopic, slug, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed: topic={} slug={} error={}",
                                urlDeletedTopic, slug, ex.getMessage());
                        failedEventService.save(urlDeletedTopic, slug, event, ex.getMessage());
                    } else {
                        log.debug("Published url.deleted: topic={} slug={} urlId={} partition={}",
                                urlDeletedTopic, slug, urlId,
                                result.getRecordMetadata().partition());
                    }
                });
    }

    private void publishClickEvent(Long urlId, String slug, UUID ownerId, HttpServletRequest request) {

        String ipHash = sha256(request.getRemoteAddr());

        UrlClickedEvent event = new UrlClickedEvent(
                urlId,
                slug,
                request.getHeader("User-Agent"),
                ipHash,
                request.getHeader("Referer"),
                Instant.now(),
                ownerId
        );

        kafkaTemplate.send(urlClickedTopic, slug, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka publish failed: topic={} slug={} error={}",
                                urlClickedTopic, slug, ex.getMessage());
                        failedEventService.save(urlClickedTopic, slug, event, ex.getMessage());
                    } else {
                        log.debug("Published url.clicked: topic={} slug={} urlId={} partition={}",
                                urlClickedTopic, slug, urlId,
                                result.getRecordMetadata().partition());
                    }
                });
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

