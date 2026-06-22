package com.shortlyai.analytics.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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

        Set<String> keysToDelete = new HashSet<>();

        ScanOptions options = ScanOptions.scanOptions()
                .match("clicks:realtime:*")
                .count(500)
                .build();

        try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection();

             Cursor<byte[]> cursor = connection.scan(options)) {

            while (cursor.hasNext()) {

                String key = new String(cursor.next(), StandardCharsets.UTF_8);

                String val = stringRedisTemplate.opsForValue().get(key);

                if ("0".equals(val)) {
                    keysToDelete.add(key);
                }
            }
        }

        if (keysToDelete.isEmpty()) {

            log.info("No zero-count keys to clean up");

            return;
        }

        stringRedisTemplate.delete(keysToDelete);

        log.info("Cleaned up {} zero-count realtime keys", keysToDelete.size());
    }
}
