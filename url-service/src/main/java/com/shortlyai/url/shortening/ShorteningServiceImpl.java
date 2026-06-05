package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;
import com.shortlyai.url.common.exception.DuplicateSlugException;
import com.shortlyai.url.common.exception.UrlNotFoundException;
import com.shortlyai.url.events.UrlCreatedEvent;
import lombok.RequiredArgsConstructor;
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

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ShorteningServiceImpl implements ShorteningService {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final KafkaTemplate<String, UrlCreatedEvent> kafkaTemplate;

    @Value("${url.base-domain}")
    private String baseDomain;

    @Value("${url.default-expiry-days}")
    private long defaultExpiryDays;

    @Value("${url.cache-ttl-seconds}")
    private long cacheTtlSeconds;

    @Value("${kafka.topics.url-created}")
    private String urlCreatedTopic;

    // Used by REDIS for caching
    private static final String CACHE_PREFIX = "url:";

    @Override
    public ShortenResponse shorten(ShortenRequest request, Long userId) {

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

        // Set custom slug if provided
        if (isCustom) {
            url.setSlug(request.customSlug());
        }

        // First save to generate database ID
        Url savedUrl = urlRepository.save(url);

        // Generate Base62 slug from ID if custom slug was not provided
        if (!isCustom) {
            savedUrl.setSlug(Base62.encode(savedUrl.getId()));
            savedUrl = urlRepository.save(savedUrl);
        }

        // Cache slug → original URL in Redis
        stringRedisTemplate.opsForValue()
                .set(
                CACHE_PREFIX + savedUrl.getSlug(),
                savedUrl.getOriginalUrl(),
                Duration.ofSeconds(cacheTtlSeconds)
        );

        // Publish kafka event
        kafkaTemplate.send(
                urlCreatedTopic,           // which mailbox
                savedUrl.getSlug(),        // routing key — same slug = same partition
                new UrlCreatedEvent(       // the message
                        savedUrl.getId(),
                        savedUrl.getSlug(),
                        savedUrl.getOriginalUrl(),
                        baseDomain + "/" + savedUrl.getSlug(),
                        savedUrl.getUserId(),
                        savedUrl.getExpiresAt(),
                        savedUrl.getCreatedAt()
                )
        );

        log.info(
                "Created short-url '{}' for userId={}",
                savedUrl.getSlug(),
                userId
        );

        // Return response DTO
        return mapToResponse(savedUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolve(String slug) {

        String cached = stringRedisTemplate.opsForValue()
                .get(CACHE_PREFIX + slug);

        // cache hit
        if (cached != null) {

            log.info("Cache hit for slug: {}", slug);

            return cached;
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
                shortUrl.getOriginalUrl(),
                Duration.ofSeconds(cacheTtlSeconds)
        );

        return shortUrl.getOriginalUrl();
    }

    @Override
    public void delete(Long id, Long userId) {

        // fetch URL
        Url url = urlRepository.findByIdAndUserId(id, userId)
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
    }

    @Override
    public ShortenResponse getUrl(Long id, Long userId) {

        Url url = urlRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        return mapToResponse(url);
    }

    @Override
    public Page<ShortenResponse> getUserUrls(Long userId, Pageable pageable) {

        return urlRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
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
}

