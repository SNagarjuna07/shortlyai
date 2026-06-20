package com.shortlyai.analytics.clicks;

import com.shortlyai.analytics.events.UrlClickedEvent;
import com.shortlyai.analytics.events.UrlCreatedEvent;
import com.shortlyai.analytics.events.UrlDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickServiceImplTest {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private BloomFilterService bloomFilterService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ClickHourlyRepository clickHourlyRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ClickServiceImpl clickService;

    @BeforeEach
    void setUp() {

        clickService = new ClickServiceImpl(
                clickEventRepository, bloomFilterService, redisTemplate, clickHourlyRepository);
    }

    @Test
    void processClick_savesEventAndIncrementsCounter_whenNotDuplicate() {

        Instant now = Instant.parse("2026-06-19T10:15:30Z");

        UrlClickedEvent event = new UrlClickedEvent(
                42L, "abc123", "Mozilla/5.0", "hash123",
                "https://google.com", "US", "NYC", now);

        String expectedFingerprint = "42:hash123:" + (now.getEpochSecond() / 60);

        when(bloomFilterService.isDuplicate(expectedFingerprint)).thenReturn(false);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        clickService.processClick(event);

        verify(clickEventRepository).save(any(ClickEvent.class));

        verify(bloomFilterService).markSeen(expectedFingerprint);

        verify(valueOperations).increment("clicks:realtime:42");
    }

    @Test
    void processClick_skipsSaving_whenDuplicate() {

        Instant now = Instant.now();

        UrlClickedEvent event = new UrlClickedEvent(
                7L, "xyz", "ua", "iphash", "ref", "IN", "Mysuru", now);

        when(bloomFilterService.isDuplicate(anyString())).thenReturn(true);

        clickService.processClick(event);

        verify(clickEventRepository, never()).save(any());

        verify(bloomFilterService, never()).markSeen(anyString());

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getTotalClicks_returnsFromRedis_whenCached() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get("clicks:realtime:99")).thenReturn("17");

        long total = clickService.getTotalClicks(99L);

        assertThat(total).isEqualTo(17L);

        verifyNoInteractions(clickEventRepository);
    }

    @Test
    void getTotalClicks_fallsBackToPostgres_whenCacheMiss() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get("clicks:realtime:99")).thenReturn(null);

        when(clickEventRepository.countByUrlId(99L)).thenReturn(5L);

        long total = clickService.getTotalClicks(99L);

        assertThat(total).isEqualTo(5L);
    }

    @Test
    void initializeCounter_setsRedisCounterIfAbsent() {

        UrlCreatedEvent event = new UrlCreatedEvent(
                1L, "slug1", "https://example.com", "http://sly.ai/slug1",
                UUID.randomUUID(), null, Instant.now());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        clickService.initializeCounter(event);

        verify(valueOperations).setIfAbsent("clicks:realtime:1", "0");
    }

    @Test
    void deleteClickData_removesPostgresRowsAndRedisCounter() {

        UrlDeletedEvent event = new UrlDeletedEvent(55L, "slug55", UUID.randomUUID(), Instant.now());

        clickService.deleteClickData(event);

        verify(clickEventRepository).deleteBySlug("slug55");

        verify(redisTemplate).delete("clicks:realtime:55");
    }

    @Test
    void getHourlyBreakdown_mapsRepositoryRowsToResponses_inOrder() {

        Instant hour1 = Instant.parse("2026-06-19T08:00:00Z");

        Instant hour2 = Instant.parse("2026-06-19T09:00:00Z");

        List<ClickHourly> rows = List.of(
                new ClickHourly(10L, hour1, 3L),
                new ClickHourly(10L, hour2, 8L)
        );

        when(clickHourlyRepository.findByUrlIdSince(eq(10L), any(Instant.class))).thenReturn(rows);

        List<HourlyBreakdownResponse> result = clickService.getHourlyBreakdown(10L, 24);

        assertThat(result).containsExactly(
                new HourlyBreakdownResponse(hour1, 3L),
                new HourlyBreakdownResponse(hour2, 8L)
        );
    }

    @Test
    void getTopUrls_passesCorrectPageable_andReturnsRepositoryResult() {

        List<TopUrlResponse> expected = List.of(new TopUrlResponse(1L, 100L), new TopUrlResponse(2L, 50L));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(clickEventRepository.findTopUrls(pageableCaptor.capture())).thenReturn(expected);

        List<TopUrlResponse> result = clickService.getTopUrls(5);

        assertThat(result).isEqualTo(expected);

        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 5));
    }
}