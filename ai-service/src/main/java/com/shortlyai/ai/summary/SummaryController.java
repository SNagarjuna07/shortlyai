package com.shortlyai.ai.summary;

import com.shortlyai.ai.summary.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/ai/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    @GetMapping("/{urlId}")
    public SummaryResponse summarize(
            @PathVariable Long urlId,
            @RequestHeader("X-User-Id") String userId
    ) {

        return summaryService.summarize(urlId, userId);
    }
}