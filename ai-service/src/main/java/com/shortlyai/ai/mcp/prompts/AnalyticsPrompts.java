package com.shortlyai.ai.mcp.prompts;

import com.shortlyai.ai.mcp.auth.McpUserContext;
import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AnalyticsPrompts {

    private static final int SUMMARY_TOP_N = 5;

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private final Resource analyticsPrompt;

    public AnalyticsPrompts(
            ResilientAnalyticsOps resilientAnalyticsOps,
            @Value("classpath:prompts/analytics-mcp-prompt/analytics-prompt.st")
            Resource analyticsPrompt
    ) {
        this.resilientAnalyticsOps = resilientAnalyticsOps;
        this.analyticsPrompt = analyticsPrompt;
    }

    @McpPrompt(
            name = "performance-summary",
            title = "Performance Summary",
            description = "Summarizes the user's top 5 performing URLs and suggests what to do next."
    )
    public GetPromptResult performanceSummary() {

        String userId = McpUserContext.get();

        log.info("MCP prompt performance-summary userId: {}", userId);

        List<AnalyticsOperationsService.TopUrlResult> topUrls = resilientAnalyticsOps
                .getTopUrls(SUMMARY_TOP_N, userId)
                .join();

        String urlLines = topUrls.isEmpty()
                ? "No URLs with recorded clicks yet."
                : topUrls.stream()
                .map(u -> "- urlId %d: %d clicks".formatted(u.urlId(), u.clickCount()))
                .collect(Collectors.joining("\n"));

        PromptTemplate template = new PromptTemplate(analyticsPrompt);

        String prompt = template
                .render(
                        Map.of(
                                "top", SUMMARY_TOP_N,
                                "urlLines", urlLines)
                );

        return new GetPromptResult(
                "Performance Summary",
                List.of(new PromptMessage(Role.USER, new TextContent(prompt))),
                null
        );
    }
}