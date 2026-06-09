package com.shortlyai.url.common.config.cache;

import com.shortlyai.url.shortening.Url;
import com.shortlyai.url.shortening.UrlRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class CacheWarmingJob {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final long ttl;

    // lock key in Redis
    private static final String WARM_LOCK_KEY = "lock:cache-warming";

    public CacheWarmingJob(
            UrlRepository urlRepository,
            StringRedisTemplate stringRedisTemplate,
            @Value("${url.cache-ttl-seconds}") long ttl) {

        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttl = ttl;
    }

    @EventListener(ApplicationReadyEvent.class) // Executes right after Spring is fully loaded
    // @SchedulerLock - doesn't work on @EventListener
    public void cacheWarmup() {

        // setIfAbsent - atomic, only ONE pod wins
        // TTL of 5 min - lock auto-releases if pod crashes mid-warmup
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(WARM_LOCK_KEY, "locked", Duration.ofMinutes(5));

        // Another pod already warming — skip
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

            mostActiveUrls.forEach(url ->
                    stringRedisTemplate.opsForValue().set(
                            "url:" + url.getSlug(),
                            url.getId() + "|" + url.getOriginalUrl(),
                            Duration.ofSeconds(ttl)
                    )
            );

            log.info("Cache warmed with {} URLs", mostActiveUrls.getNumberOfElements());

        } finally {

            // Always release lock - even if warmup fails halfway
            stringRedisTemplate.delete(WARM_LOCK_KEY);
        }
    }
}
