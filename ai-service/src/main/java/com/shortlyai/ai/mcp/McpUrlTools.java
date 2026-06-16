package com.shortlyai.ai.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class McpUrlTools {

    private final RestClient urlServiceClient;

    private final String apiPrefix;

    public McpUrlTools(
            @Qualifier("urlServiceClient") RestClient urlServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.urlServiceClient = urlServiceClient;
        this.apiPrefix = apiPrefix;
    }

    @Tool(description = """
            Shorten a long URL. Returns the generated short URL and its numeric urlId.
            Requires the caller's userId (UUID from their JWT subject claim).
            """)
    public String shortenUrl(
            @ToolParam(description = "The original long URL to shorten (must include http:// or https://)") String originalUrl,
            @ToolParam(description = "Authenticated user UUID - obtain from the user's JWT sub claim") String userId
    ) {

        record ShortenRequest(String originalUrl) {}

        record ShortenResponse(Long id, String slug, String shortUrl) {}

        log.info("MCP shortenUrl userId: {}, url: {}", userId, originalUrl);

        ShortenResponse response = urlServiceClient.post()
                .uri(apiPrefix + "/urls")
                .header("X-User-Id", userId)
                .body(new ShortenRequest(originalUrl))
                .retrieve()
                .body(ShortenResponse.class);

        log.debug("MCP shortenUrl result slug: {}, id: {}", response.slug(), response.id());

        return "Short URL created: %s (urlId: %d, slug: %s)"
                .formatted(response.shortUrl(), response.id(), response.slug());
    }

    @Tool(description = """
            Get details of a shortened URL by its slug.
            Returns the original URL, short URL, and total click count.
            """)
    public String getUrlDetails(
            @ToolParam(description = "The short slug (e.g. abc123)") String slug,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record UrlDetails(Long id, String slug, String originalUrl, String shortUrl, long clickCount) {}

        log.info("MCP getUrlDetails userId: {}, slug: {}", userId, slug);

        UrlDetails details = urlServiceClient.get()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .body(UrlDetails.class);

        return "urlId: %d | slug: %s | original: %s | short: %s | clicks: %d"
                .formatted(details.id(), details.slug(), details.originalUrl(),
                        details.shortUrl(), details.clickCount());
    }

    @Tool(description = """
            Permanently delete a shortened URL by its slug.
            This action is irreversible - always confirm with the user before calling.
            """)
    public String deleteUrl(
            @ToolParam(description = "The slug of the URL to delete") String slug,
            @ToolParam(description = "Authenticated user UUID - must be the URL's owner") String userId
    ) {

        log.warn("MCP deleteUrl userId: {}, slug: {}", userId, slug);

        urlServiceClient.delete()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .toBodilessEntity();

        log.info("MCP deleteUrl completed userId: {}, slug: {}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }
}