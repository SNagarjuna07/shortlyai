package com.shortlyai.ai.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortlyai.ai.mcp.auth.McpUserContext;
import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlResources {

    private final ResilientUrlOps resilientUrlOps;

    private final ObjectMapper objectMapper;

    // Real record, not a hand-built string - Jackson handles escaping, so a
    // slug/originalUrl containing a quote can't produce broken JSON.
    public record UrlResource(String slug, String originalUrl, String shortUrl, long clickCount) {
    }

    @McpResource(
            uri = "shortly://url/{slug}",
            name = "url-details",
            title = "URL Details",
            description = "Full details (original URL, short URL, click count) for one shortened URL owned by the authenticated user.",
            mimeType = "application/json"
    )
    public String getUrl(
            @McpArg(name = "slug", description = "Short URL slug (e.g. abc123)", required = true)
            String slug
    ) {

        String userId = McpUserContext.get();

        log.info("MCP resource url-details userId: {}, slug: {}", userId, slug);

        UrlOperationsService.UrlDetails details = resilientUrlOps
                .getDetails(slug, userId)
                .join();

        if (details == null) {

            throw new IllegalArgumentException(
                    "No URL found for slug '%s'"
                            .formatted(slug)
            );
        }

        return writeJson(
                new UrlResource(
                        details.slug(),
                        details.originalUrl(),
                        details.shortUrl(),
                        details.clickCount()
                )
        );
    }

    @McpResource(
            uri = "shortly://urls",
            name = "url-list",
            title = "All My URLs",
            description = "All shortened URLs owned by the authenticated user.",
            mimeType = "application/json"
    )
    public String listUrls() {

        String userId = McpUserContext.get();

        log.info("MCP resource url-list userId: {}", userId);

        List<UrlResource> allUrls = resilientUrlOps
                .getAllForUser(userId)
                .join();

        List<UrlResource> resources = allUrls.stream()
                .map(d ->
                        new UrlResource(
                                d.slug(),
                                d.originalUrl(),
                                d.shortUrl(),
                                d.clickCount()
                        )
                )
                .toList();

        return writeJson(resources);
    }

    private String writeJson(Object value) {

        try {

            return objectMapper.writeValueAsString(value);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Failed to serialize MCP resource payload", e);
        }
    }
}