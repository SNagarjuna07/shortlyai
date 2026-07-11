package com.shortlyai.ai.mcp.prompts;

import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UrlPrompts {

    @McpPrompt(
            name = "analyze-url",
            title = "Analyze URL",
            description = "Analyzes a shortened URL"
    )
    public GetPromptResult analyze(

            @McpArg(
                    name = "shortCode",
                    description = "URL short code",
                    required = true
            )
            String shortCode
    ) {

        String prompt = """
                Analyze the shortened URL %s.
                
                Include:
                - Popularity
                - Security concerns
                - Recommendations
                """.formatted(shortCode);

        return new GetPromptResult(
                "URL Analysis",
                List.of(
                        new PromptMessage(
                                Role.USER,
                                new TextContent(prompt)
                        )
                ),
                null);
    }
}