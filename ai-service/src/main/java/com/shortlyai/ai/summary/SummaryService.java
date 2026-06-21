package com.shortlyai.ai.summary;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import com.shortlyai.ai.summary.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;
    private final ResilientAnalyticsOps resilientAnalyticsOps;

    public SummaryResponse summarize(Long urlId, String userId) {

        log.info("Generating summary for urlId={} userId={}", urlId, userId);

        try {

            AnalyticsOperationsService.StatsResult stats =
                    resilientAnalyticsOps
                            .getStats(urlId, userId)
                            .join();

            // Circuit breaker open, timeout, retries exhausted, etc.
            if (stats == null) {

                log.warn(
                        "analytics-service unavailable while generating summary for urlId={}",
                        urlId
                );

                return summarizeFallback();
            }

            String prompt = """
                    Write a short, friendly 2-sentence summary of this URL's
                    performance for a dashboard.

                    Total clicks: %d
                    """.formatted(stats.totalClicks());

            String text = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("Summary generated for urlId={}: {}", urlId, text);

            return new SummaryResponse(text);

        } catch (CompletionException ex) {

            log.error(
                    "Failed to fetch analytics while generating summary for urlId={}",
                    urlId,
                    ex
            );

            return summarizeFallback();

        } catch (Exception ex) {

            log.error(
                    "Unexpected error while generating summary for urlId={}",
                    urlId,
                    ex
            );

            return summarizeFallback();
        }
    }

    private SummaryResponse summarizeFallback() {

        return new SummaryResponse(
                "Performance statistics are temporarily unavailable. Please check back shortly."
        );
    }
}