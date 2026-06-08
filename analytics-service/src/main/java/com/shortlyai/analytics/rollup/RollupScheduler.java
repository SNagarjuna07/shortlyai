package com.shortlyai.analytics.rollup;

import com.shortlyai.analytics.clicks.ClickEventRepository;
import com.shortlyai.analytics.clicks.ClickHourly;
import com.shortlyai.analytics.clicks.ClickHourlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollupScheduler {

    private final ClickEventRepository clickEventRepository;
    private final ClickHourlyRepository clickHourlyRepository;

    // Runs at 5 minutes past every hour e.g. 14:05, 15:05
    // Aggregates the PREVIOUS hour's clicks into click_hourly table
    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(
            name = "analytics_hourly_rollup",   // lock name in Redis — must be unique
            lockAtLeastFor = "4m",              // hold lock at least 4 min (prevents double-run)
            lockAtMostFor = "9m"               // release after 9 min even if job hangs
    )
    @Transactional
    public void rollupPreviousHour() {
        // Calculate previous hour window e.g. if now=14:05, window=13:00 → 14:00
        Instant hourTo = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant hourFrom = hourTo.minus(1, ChronoUnit.HOURS);

        log.info("Starting hourly rollup for window {} -> {}", hourFrom, hourTo);

        // Get all unique urlIds that had clicks in this hour
        List<Long> urlIds = clickEventRepository
                .findDistinctUrlIdsBetween(hourFrom, hourTo);

        for (Long urlId : urlIds) {
            long count = clickEventRepository
                    .countByUrlIdAndClickedAtBetween(urlId, hourFrom, hourTo);

            if (count == 0) continue; // nothing to rollup

            // Upsert: update existing row or create new one
            ClickHourly row = clickHourlyRepository
                    .findByUrlIdAndHour(urlId, hourFrom)
                    .orElse(new ClickHourly(urlId, hourFrom, 0L));

            row.setClickCount(count);
            clickHourlyRepository.save(row);
        }

        log.info("Hourly rollup complete. Processed {} URLs", urlIds.size());
    }
}