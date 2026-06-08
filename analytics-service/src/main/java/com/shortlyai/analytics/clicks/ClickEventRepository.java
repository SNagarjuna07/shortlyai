package com.shortlyai.analytics.clicks;

import org.springframework.data.domain.Pageable;
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

    // Top N URLs by total click count — groups all click_events by urlId
    // Pageable controls the limit e.g. PageRequest.of(0, 10) = top 10
    // Constructor expression maps result directly to TopUrlResponse record
    @Query("""
            SELECT new com.shortlyai.analytics.clicks.TopUrlResponse(c.urlId, COUNT(c))
            FROM ClickEvent c
            GROUP BY c.urlId
            ORDER BY COUNT(c) DESC
            """)
    List<TopUrlResponse> findTopUrls(Pageable pageable);
}