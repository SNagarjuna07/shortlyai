package com.shortlyai.analytics.clicks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    // Count total clicks for a URL — used by stats endpoint
    long countByUrlId(UUID urlId);

    // Count clicks in a time window — used by hourly rollup job
    @Query("""
            SELECT COUNT(c) FROM ClickEvent c
            WHERE c.urlId = :urlId
            AND c.clickedAt >= :from
            AND c.clickedAt < :to
            """)
    long countByUrlIdAndClickedAtBetween(
            @Param("urlId") UUID urlId,
            @Param("from")  Instant from,
            @Param("to")    Instant to
    );

    @Query("""
            SELECT DISTINCT c.urlId 
            FROM ClickEvent c 
            WHERE c.clickedAt >= :from 
            AND c.clickedAt < :to
            """)
    List<UUID> findDistinctUrlIdsBetween(@Param("from") Instant from, @Param("to") Instant to);

    // Bulk delete all click events for a URL — called on url.deleted
    // Spring Data generates: DELETE FROM click_events WHERE slug = ?
    // @Modifying required for DELETE — tells Spring this mutates data
    @Modifying
    void deleteBySlug(String slug);
}