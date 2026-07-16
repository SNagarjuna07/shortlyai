package com.shortlyai.analytics.clicks;

import com.shortlyai.analytics.events.UrlClickedEvent;
import com.shortlyai.analytics.events.UrlCreatedEvent;
import com.shortlyai.analytics.events.UrlDeletedEvent;

import java.util.List;
import java.util.UUID;

public interface ClickService {

    void processClick(UrlClickedEvent event);

    long getTotalClicks(Long urlId, UUID userId);

    void initializeCounter(UrlCreatedEvent event);

    void deleteClickData(UrlDeletedEvent event);

    List<HourlyBreakdownResponse> getHourlyBreakdown(Long urlId, UUID userId, int hours);

    List<TopUrlResponse> getTopUrls(UUID userId, int limit);
}