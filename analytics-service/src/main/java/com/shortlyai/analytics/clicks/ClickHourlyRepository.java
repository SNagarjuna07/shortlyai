package com.shortlyai.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClickHourlyRepository extends JpaRepository<ClickHourly, Long> {

    // Find existing rollup row — for upsert during rollup job
    Optional<ClickHourly> findByUrlIdAndHour(Long urlId, Instant hour);

    // All hourly buckets for a URL from a point in time forward
    // Service passes Instant.now().minus(24, HOURS) for last 24h
    // Results ordered ascending — oldest hour first for chart rendering
    @Query("""
            SELECT c FROM ClickHourly c
            WHERE c.urlId = :urlId
            AND c.hour >= :since
            ORDER BY c.hour ASC
            """)
    List<ClickHourly> findByUrlIdSince(
            @Param("urlId") Long urlId,
            @Param("since") Instant since
    );
}