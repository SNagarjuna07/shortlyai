package com.shortlyai.ai.summary;

import com.shortlyai.ai.summary.dto.SummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;
    private final RestClient analyticsServiceClient;
    private final String apiPrefix;

    public SummaryService(
            ChatClient chatClient,
            @Qualifier("analyticsServiceClient") RestClient analyticsServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.chatClient = chatClient;
        this.analyticsServiceClient = analyticsServiceClient;
        this.apiPrefix = apiPrefix;
    }

    public SummaryResponse summarize(Long urlId, String userId) {

        log.info("Generating summary for urlId: {}, userId: {}", urlId, userId);

        // shape this record to match analytics-service's actual stats response
        record StatsResponse(long urlId, long totalClicks, String message) {}

        StatsResponse stats = analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/{urlId}", urlId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(StatsResponse.class);

        String prompt = """
                Write a short, friendly 2-sentence summary of this URL's
                performance for the dashboard.
                
                Total clicks: %d
                """.formatted(stats.totalClicks());

        String text = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        log.debug("Summary generated for urlId: {}: {}", urlId, text);

        return new SummaryResponse(text);
    }
}