package com.shortlyai.analytics.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor

// Deletes the URLS in Redis whose click-count is 0 (generated short-url, but never clicked)
public class RealTimeCleanupJob {

    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 30 * 60 * 1000) // every 30 min
    @SchedulerLock(name = "realtime_redis_cleanup", lockAtLeastFor = "1m", lockAtMostFor = "5m")
    public void cleanupZeroCounters() {

        log.info("Starting zero URL click counters cleanup..");

        Set<String> keys = stringRedisTemplate.keys("clicks:realtime:*");

        // Nothing in Redis
        if (keys == null || keys.isEmpty()) {

            log.info("No realtime keys found, skipping cleanup");

            return;
        }

        List<String> keyList = new ArrayList<>(keys);

        List<String> values = stringRedisTemplate
                .opsForValue()
                .multiGet(keyList);

        Set<String> keysToDelete = new HashSet<>();

        // Filter keys whose value is "0" - initialized but never clicked
        for (int i = 0; i < keyList.size(); i++) {

            if ("0".equals(values.get(i))) {

                keysToDelete.add(keyList.get(i));
            }
        }

        // No key (URL) found with 0 clicks
        if (keysToDelete.isEmpty()) {

            log.info("No zero-count keys to clean up");

            return;
        }

        stringRedisTemplate.delete(keysToDelete);

        log.info("Cleaned up {} zero-count realtime keys", keysToDelete.size());
    }
}
