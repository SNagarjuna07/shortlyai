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

    public CacheWarmingJob(
            UrlRepository urlRepository,
            StringRedisTemplate stringRedisTemplate,
            @Value("${url.cache-ttl-seconds}") long ttl) {

        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ttl = ttl;
    }

    @EventListener(ApplicationReadyEvent.class) // Executes right after Spring is fully loaded
    @SchedulerLock(name = "cacheWarmingLock", lockAtMostFor = "5m")
    public void cacheWarmup() {

        // Fetch URLs
        Page<Url> mostActiveUrls = urlRepository
                .findByIsActiveTrueOrderByClickCountDesc(PageRequest.of(0, 100));

        // Set in Redis
        mostActiveUrls.forEach(url ->
                stringRedisTemplate.opsForValue().set(
                        "url:" + url.getSlug(),   // key
                        url.getOriginalUrl(),      // value - what redirect needs
                        Duration.ofSeconds(ttl)
                )
        );

        log.info("Cache warmed with {} URLs", mostActiveUrls.getNumberOfElements());
    }
}
