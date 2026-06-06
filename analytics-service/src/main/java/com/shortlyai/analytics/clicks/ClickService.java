package com.shortlyai.analytics.clicks;

import com.shortlyai.analytics.events.UrlClickedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickService {

    private final ClickEventRepository clickEventRepository;
    private final BloomFilterService bloomFilterService;
    private final RedisTemplate<String, String> redisTemplate;

    // Called by Kafka consumer for every url.clicks event
    @Transactional  // wraps DB insert in a transaction — rolls back if save fails
    public void processClick(UrlClickedEvent event) {
        // Build a fingerprint: urlId + ipHash + minute-bucket
        // Deduplicates same IP clicking same URL within the same minute
        String fingerprint = event.urlId() + ":" + event.ipHash() + ":"
                + (event.clickedAt().getEpochSecond() / 60); // minute bucket

        if (bloomFilterService.isDuplicate(fingerprint)) {
            log.debug("Duplicate click detected for slug={}, skipping", event.slug());
            return; // skip — already counted this click
        }

        // Save raw click event to Postgres
        ClickEvent clickEvent = ClickEvent.from(event);
        clickEventRepository.save(clickEvent);

        // Mark as seen in Bloom filter
        bloomFilterService.markSeen(fingerprint);

        // Increment real-time Redis counter — key: "clicks:realtime:{urlId}"
        // Used by dashboard for live counter without hitting Postgres
        String redisKey = "clicks:realtime:" + event.urlId();
        redisTemplate.opsForValue().increment(redisKey);

        log.debug("Processed click for slug={} urlId={}", event.slug(), event.urlId());
    }

    // Called by stats controller — total click count for a URL
    @Transactional(readOnly = true) // readOnly = true Hibernate skips dirty checking, faster
    public long getTotalClicks(UUID urlId) {
        // Try Redis first (real-time counter) — fast path
        String redisKey = "clicks:realtime:" + urlId;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        // Fallback: count from Postgres
        return clickEventRepository.countByUrlId(urlId);
    }
}