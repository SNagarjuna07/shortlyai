package com.shortlyai.ai.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    // Spring AI MCP auto-config picks up ToolCallbackProvider beans by type
    // and registers their @Tool methods as MCP tool specs on the server.
    //
    // NOTE: AgentService uses explicit .tools(urlServiceTools, analyticsServiceTools)
    // which REPLACES any auto-injected defaults - agent behavior unaffected.
    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            McpUrlTools mcpUrlTools,
            McpAnalyticsTools mcpAnalyticsTools
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpUrlTools, mcpAnalyticsTools)
                .build();
    }
}