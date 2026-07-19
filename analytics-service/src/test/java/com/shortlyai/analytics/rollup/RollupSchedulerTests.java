package com.shortlyai.analytics.rollup;

import com.shortlyai.analytics.clicks.ClickEventRepository;
import com.shortlyai.analytics.clicks.ClickHourly;
import com.shortlyai.analytics.clicks.ClickHourlyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RollupSchedulerTests {

    @Mock
    ClickEventRepository clickEventRepository;

    @Mock
    ClickHourlyRepository clickHourlyRepository;

    RollupScheduler rollupScheduler;

    @BeforeEach
    void setUp() {
        rollupScheduler = new RollupScheduler(clickEventRepository, clickHourlyRepository);
    }

    @Test
    void rollupPreviousHour_newUrlWithClicks_createsNewHourlyRow() {

        when(clickEventRepository.findDistinctUrlIdsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(100L));

        when(clickEventRepository.countByUrlIdAndClickedAtBetween(eq(100L), any(Instant.class), any(Instant.class)))
                .thenReturn(25L);

        when(clickHourlyRepository.findByUrlIdAndHour(eq(100L), any(Instant.class)))
                .thenReturn(Optional.empty());

        rollupScheduler.rollupPreviousHour();

        ArgumentCaptor<ClickHourly> captor = ArgumentCaptor.forClass(ClickHourly.class);
        verify(clickHourlyRepository).save(captor.capture());

        assertThat(captor.getValue().getUrlId()).isEqualTo(100L);
        assertThat(captor.getValue().getClickCount()).isEqualTo(25L);
    }

    @Test
    void rollupPreviousHour_existingHourlyRow_updatesInPlaceRatherThanDuplicating() {

        ClickHourly existing = new ClickHourly(200L, Instant.now(), 10L);

        when(clickEventRepository.findDistinctUrlIdsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(200L));

        when(clickEventRepository.countByUrlIdAndClickedAtBetween(eq(200L), any(Instant.class), any(Instant.class)))
                .thenReturn(30L);

        when(clickHourlyRepository.findByUrlIdAndHour(eq(200L), any(Instant.class)))
                .thenReturn(Optional.of(existing));

        rollupScheduler.rollupPreviousHour();

        verify(clickHourlyRepository).save(existing);
        assertThat(existing.getClickCount()).isEqualTo(30L); // overwritten, not added to
    }

    @Test
    void rollupPreviousHour_zeroClicksInWindow_skipsUpsertEntirely() {

        when(clickEventRepository.findDistinctUrlIdsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(300L));

        when(clickEventRepository.countByUrlIdAndClickedAtBetween(eq(300L), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);

        rollupScheduler.rollupPreviousHour();

        verify(clickHourlyRepository, never()).findByUrlIdAndHour(any(), any());
        verify(clickHourlyRepository, never()).save(any());
    }

    @Test
    void rollupPreviousHour_noUrlsWithActivity_doesNothing() {

        when(clickEventRepository.findDistinctUrlIdsBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        rollupScheduler.rollupPreviousHour();

        verify(clickHourlyRepository, never()).save(any());
    }
}