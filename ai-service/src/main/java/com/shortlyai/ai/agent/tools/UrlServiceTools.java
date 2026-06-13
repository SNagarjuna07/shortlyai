package com.shortlyai.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class UrlServiceTools {

    private final RestClient urlServiceClient;

    private final String apiPrefix;

    public UrlServiceTools(
            @Qualifier("urlServiceClient") RestClient urlServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.urlServiceClient = urlServiceClient;
        this.apiPrefix = apiPrefix;
    }

    @Tool(description = "Shorten a long URL and return the generated short URL")
    public String shortenUrl(
            @ToolParam(description = "the original long URL") String originalUrl,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool shortenUrl invoked userId: {}, originalUrl: {}", userId, originalUrl);

        record ShortenRequest(String originalUrl) {}

        record ShortenResponse(Long id, String slug, String shortUrl) {}

        ShortenResponse response = urlServiceClient.post()
                .uri(apiPrefix + "/urls")
                .header("X-User-Id", userId)
                .body(new ShortenRequest(originalUrl))
                .retrieve()
                .body(ShortenResponse.class);

        log.debug("shortenUrl result userId: {}, slug: {}, urlId: {}",
                userId, response.slug(), response.id()
        );

        return "Short URL created: %s (urlId: %d)".formatted(response.shortUrl(), response.id());
    }

    @Tool(description = "Get details of a shortened URL by its slug")
    public String getUrlDetails(
            @ToolParam(description = "the short slug, e.g. abc123") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlDetails invoked userId: {}, slug: {}", userId, slug);

        // ignoreUnknown=true — ShortenResponse has more fields (userId, isCustom,
        // expiresAt, createdAt) we don't need; without this, extra JSON fields
        // throw UnrecognizedPropertyException
        @JsonIgnoreProperties(ignoreUnknown = true)
        record UrlDetails(Long id, String slug, String originalUrl, String shortUrl, long clickCount) {
        }

        UrlDetails details = urlServiceClient.get()
                .uri(apiPrefix + "/urls/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .body(UrlDetails.class);

        log.debug("getUrlDetails result userId: {}, slug: {}, clicks: {}",
                userId, slug, details.clickCount()
        );

        // urlId included explicitly so the LLM can extract it and chain into getUrlStats
        return "urlId: %d, Slug: %s, Original URL: %s, Clicks: %d"
                .formatted(details.id(), details.slug(), details.originalUrl(), details.clickCount());
    }

    @Tool(description = "Delete a shortened URL by its slug")
    public String deleteUrl(
            @ToolParam(description = "the short slug to delete") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.warn("Tool deleteUrl invoked userId: {}, slug: {}", userId, slug);

        urlServiceClient.delete()
                .uri(apiPrefix + "/urls/delete/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .toBodilessEntity();

        log.info("deleteUrl completed userId: {}, slug: {}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }
}