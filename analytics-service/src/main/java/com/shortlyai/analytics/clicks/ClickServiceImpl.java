package com.shortlyai.analytics.clicks;

import com.shortlyai.analytics.events.UrlClickedEvent;
import com.shortlyai.analytics.events.UrlCreatedEvent;
import com.shortlyai.analytics.events.UrlDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickService {

    private final ClickEventRepository clickEventRepository;
    private final BloomFilterService bloomFilterService;
    private final StringRedisTemplate redisTemplate;
    private final ClickHourlyRepository clickHourlyRepository;

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
    public long getTotalClicks(Long urlId) {
        // Try Redis first (real-time counter) — fast path
        String redisKey = "clicks:realtime:" + urlId;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        // Fallback: count from Postgres
        return clickEventRepository.countByUrlId(urlId);
    }

    // Called on url.created — sets counter to 0 so stats endpoint works immediately
    public void initializeCounter(UrlCreatedEvent event) {

        String redisKey = "clicks:realtime:" + event.urlId();

        // Only set if not already exists - don't overwrite real click data
        redisTemplate.opsForValue().setIfAbsent(redisKey, "0");

        log.debug("Initialized click counter for slug: {} urlId: {}", event.slug(), event.urlId());
    }

    @Transactional
    public void deleteClickData(UrlDeletedEvent event) {

        // Delete all raw click rows for this slug
        clickEventRepository.deleteBySlug(event.slug());

        // Delete real-time Redis counter
        redisTemplate.delete("clicks:realtime:" + event.id());

        log.info("Deleted click data for slug: {} urlId: {}", event.slug(), event.id());
    }

    // Queries hourly rollup table for a URL over last N hours
    @Transactional(readOnly = true)
    public List<HourlyBreakdownResponse> getHourlyBreakdown(Long urlId, int hours) {

        List<ClickHourly> clicks = clickHourlyRepository.findByUrlIdSince(
                urlId,
                Instant.now()
                        .minus(hours, ChronoUnit.HOURS)
        );

        return clicks
                .stream()
                .map(row ->
                        new HourlyBreakdownResponse(
                                row.getHour(),
                                row.getClickCount()
                        )
                )
                .toList();

    }

    // Queries raw click_events grouped by urlId. Returns top N by click count
    @Transactional(readOnly = true)
    public List<TopUrlResponse> getTopUrls(int limit) {

        return clickEventRepository.findTopUrls(
                PageRequest.of(
                        0,
                        limit
                )
        );
    }
}