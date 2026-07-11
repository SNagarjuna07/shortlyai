package com.shortlyai.ai.mcp.resources;


import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
public class UrlResources {

    @McpResource(
            uri = "shortly://stats/{shortCode}",
            name = "url-stats",
            title = "URL Statistics",
            description = "Returns statistics for a shortened URL",
            mimeType = "application/json"
    )
    public String getStats(
            @McpArg(
                    name = "shortCode",
                    description = "Short URL code",
                    required = true
            )
            String shortCode
    ) {

        return """
                {
                    "shortCode":"%s",
                    "clicks":123,
                    "created":"2026-07-10"
                }
                """.formatted(shortCode);
    }
}