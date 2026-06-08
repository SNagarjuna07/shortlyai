package com.shortlyai.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    // Count total clicks for a URL — fallback when Redis misses
    long countByUrlId(Long urlId);

    // Count clicks in time window — used by hourly rollup job
    @Query("""
            SELECT COUNT(c) FROM ClickEvent c
            WHERE c.urlId = :urlId
            AND c.clickedAt >= :from
            AND c.clickedAt < :to
            """)
    long countByUrlIdAndClickedAtBetween(
            @Param("urlId") Long urlId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // All urlIds with clicks in window — rollup job iterates these
    @Query("""
            SELECT DISTINCT c.urlId
            FROM ClickEvent c
            WHERE c.clickedAt >= :from
            AND c.clickedAt < :to
            """)
    List<Long> findDistinctUrlIdsBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Bulk delete all clicks for a URL — called on url.deleted event
    @Modifying
    void deleteBySlug(String slug);
}