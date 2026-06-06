package com.shortlyai.analytics.clicks;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/analytics")
@RequiredArgsConstructor
public class ClickStatsController {

    private final ClickService clickService; // thin controller — one call, done

    // GET /api/v1/analytics/{urlId} → returns total click count
    @GetMapping("/{urlId}")
    public ResponseEntity<ClickStatsResponse> getStats(@PathVariable UUID urlId) {
        long total = clickService.getTotalClicks(urlId);
        return ResponseEntity.ok(new ClickStatsResponse(urlId, total, "OK"));
    }
}