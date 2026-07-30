package com.shortlyai.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClickHourlyRepository extends JpaRepository<ClickHourly, Long> {

    // Find existing rollup row — kept for other callers, no longer used in the hot loop
    Optional<ClickHourly> findByUrlIdAndHour(Long urlId, Instant hour);
    List<ClickHourly> findByUrlIdInAndHour(List<Long> urlIds, Instant hour);

    // All hourly buckets for a URL from a point in time forward
    @Query("""
            SELECT c FROM ClickHourly c
            WHERE c.urlId = :urlId
            AND c.hour >= :since
            ORDER BY c.hour ASC
            """)
    List<ClickHourly> findByUrlIdAndSince(
            @Param("urlId") Long urlId,
            @Param("since") Instant since
    );
}