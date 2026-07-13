package com.shortlyai.ai.summary;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import com.shortlyai.ai.summary.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletionException;

@Service
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private final Resource summaryPrompt;

    public SummaryService(
            ChatClient chatClient,
            ResilientAnalyticsOps resilientAnalyticsOps,
            @Value("classpath:prompts/summary-service-prompt/summary-prompt.st")
            Resource summaryPrompt
    ) {
        this.chatClient = chatClient;
        this.resilientAnalyticsOps = resilientAnalyticsOps;
        this.summaryPrompt = summaryPrompt;
    }

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

            PromptTemplate template = new PromptTemplate(summaryPrompt);

            String prompt = template
                    .render(
                            Map.of("clicks", stats.totalClicks())
                    );

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