package com.shortlyai.url.expiry;

import com.shortlyai.url.shortening.Url;
import com.shortlyai.url.shortening.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExpiryCleanupJob {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "urlExpiryCleanup", lockAtMostFor = "5m")
    @Transactional
    public void cleanupExpiredUrls() {

        log.info("Starting expired URLs cleanup..");

        List<Url> expiredUrls = urlRepository.findAllExpired(Instant.now());

        // Deactivate all in one query — efficient
        int count = urlRepository.deactivateExpiredUrls(Instant.now());

        // Evict each from Redis cache
        expiredUrls.forEach(url ->
                stringRedisTemplate.delete("url:" + url.getSlug())
        );

        log.info("Expired URLs cleanup completed - deactivated {} URLs", count);
    }
}
