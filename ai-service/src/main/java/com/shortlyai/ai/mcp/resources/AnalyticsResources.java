package com.shortlyai.ai.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlyai.ai.mcp.auth.McpUserContext;
import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsResources {

    private static final int TOP_URLS_RESOURCE_LIMIT = 10;

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private final ObjectMapper objectMapper;

    public record TopUrlEntry(long urlId, long clickCount) {}

    @McpResource(
            uri = "shortly://analytics/top-urls",
            name = "top-urls",
            title = "Top Performing URLs",
            description = "The authenticated user's top 10 shortened URLs ranked by click count, as ambient context (not a tool call).",
            mimeType = "application/json"
    )
    public String getTopUrls() {

        String userId = McpUserContext.get();

        log.info("MCP resource top-urls userId: {}", userId);

        List<AnalyticsOperationsService.TopUrlResult> topUrls = resilientAnalyticsOps
                .getTopUrls(TOP_URLS_RESOURCE_LIMIT, userId)
                .join();

        List<TopUrlEntry> entries = topUrls.stream()
                .map(t ->
                        new TopUrlEntry(
                                t.urlId(),
                                t.clickCount()
                        )
                )
                .toList();

        try {

            return objectMapper.writeValueAsString(entries);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Failed to serialize top-urls resource payload", e);
        }
    }
}