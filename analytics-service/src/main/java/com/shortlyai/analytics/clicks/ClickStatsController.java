package com.shortlyai.analytics.clicks;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/analytics")
@RequiredArgsConstructor
public class ClickStatsController {

    private final ClickService clickService;

    // GET /api/v1/analytics/{urlId} -> returns total click count
    @GetMapping("/{urlId}")
    public ResponseEntity<ClickStatsResponse> getStats(@PathVariable Long urlId) {
        long total = clickService.getTotalClicks(urlId);
        return ResponseEntity.ok(new ClickStatsResponse(urlId, total, "OK"));
    }

    // hourly breakdown
    @GetMapping("/{urlId}/hourly")
    public ResponseEntity<List<HourlyBreakdownResponse>> getHourlyBreakdown(
            @PathVariable Long urlId,
            @RequestParam(defaultValue = "24") int hours
    ) {

        return ResponseEntity.ok(
                clickService.getHourlyBreakdown(urlId, hours)
        );
    }

    // Top N URLs
    @GetMapping("/top")
    public ResponseEntity<List<TopUrlResponse>> topUrls(
            @RequestParam(defaultValue = "10") int limit
    ) {

        return ResponseEntity.ok(
                clickService.getTopUrls(limit)
        );
    }
}