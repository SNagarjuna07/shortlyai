package com.shortlyai.url.common.config.cache;

import com.shortlyai.url.shortening.Url;
import com.shortlyai.url.shortening.UrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class CacheWarmingJob {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final long ttl;

    private static final String WARM_LOCK_KEY = "lock:cache-warming";

    // \u0000 (null byte)
    // Format: "urlId\u0000userId\u0000expiresAtEpochMs\u0000originalUrl"
    private static final String CACHE_SEP = "\u0000";

    public CacheWarmingJob(
            UrlRepository urlRepository,
            StringRedisTemplate stringRedisTemplate,
            @Value("${url.cache-ttl-seconds}") long ttl) {

        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttl = ttl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cacheWarmup() {

        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(WARM_LOCK_KEY, "locked", Duration.ofMinutes(5));

        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Cache warming already running on another instance, skipping");
            return;
        }

        try {

            Page<Url> mostActiveUrls = urlRepository
                    .findByIsActiveTrueOrderByClickCountDesc(
                            PageRequest.of(
                                    0,
                                    100
                            )
                    );

            Instant now = Instant.now();

            long warmedCount = mostActiveUrls.stream()
                    .filter(url -> url.getExpiresAt() == null || url.getExpiresAt().isAfter(now))
                    .count();

            log.info("Cache warmed with {} URLs (skipped {} expired)",
                    warmedCount, mostActiveUrls.getNumberOfElements() - warmedCount);

        } finally {
            stringRedisTemplate.delete(WARM_LOCK_KEY);
        }
    }
}