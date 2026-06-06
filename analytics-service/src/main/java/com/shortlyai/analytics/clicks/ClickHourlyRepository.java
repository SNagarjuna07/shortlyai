package com.shortlyai.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClickHourlyRepository extends JpaRepository<ClickHourly, Long> {

    // Find existing rollup row — for upsert during rollup job
    Optional<ClickHourly> findByUrlIdAndHour(UUID urlId, Instant hour);

    // All distinct urlIds that had clicks in a time window — rollup job iterates these
    @Query("SELECT DISTINCT c.urlId FROM ClickHourly c WHERE c.hour >= :from")
    List<UUID> findDistinctUrlIdsSince(Instant from);
}