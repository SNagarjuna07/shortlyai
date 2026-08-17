package com.shortlyai.url.expiry;

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

        Instant now = Instant.now();

        List<String> expiredSlugs = urlRepository.findExpiredSlugs(now);

        // Check if it is empty
        if (expiredSlugs.isEmpty()) {

            log.info("No expired URLs found, skipping");

            return;
        }

        // Deactivate all in one query - efficient
        int count = urlRepository.deactivateExpiredUrls(now);

        List<String> cacheKeys = expiredSlugs.stream()
                .map(slug -> "url:" + slug)
                .toList();

        stringRedisTemplate.delete(cacheKeys);

        log.info("Expired URLs cleanup completed - deactivated {} URLs", count);
    }
}