package com.shortlyai.ai.agent.tools;

import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlServiceTools {

    private final ResilientUrlOps resilientUrlOps;

    @Tool(description = "Shorten a long URL and return the generated short URL")
    public String shortenUrl(
            @ToolParam(description = "the original long URL") String originalUrl,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool shortenUrl userId={} url={}", userId, originalUrl);

        UrlOperationsService.ShortenResult result = resilientUrlOps.shorten(originalUrl, userId);

        if (result == null) {
            return "URL shortening temporarily unavailable - please try again shortly.";
        }

        log.debug("shortenUrl slug={} urlId={}", result.slug(), result.id());

        return "Short URL created: %s (urlId: %d)".formatted(result.shortUrl(), result.id());
    }

    @Tool(description = "Get details of a shortened URL by its slug")
    public String getUrlDetails(
            @ToolParam(description = "the short slug, e.g. abc123") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlDetails userId={} slug={}", userId, slug);

        UrlOperationsService.UrlDetails details = resilientUrlOps.getDetails(slug, userId);

        if (details == null) {
            return "Could not retrieve details for '%s' - url-service temporarily unavailable."
                    .formatted(slug);
        }

        log.debug("getUrlDetails slug={} clicks={}", slug, details.clickCount());

        return "urlId: %d, slug: %s, original URL: %s, clicks: %d"
                .formatted(details.id(), details.slug(), details.originalUrl(), details.clickCount());
    }

    @Tool(description = "Delete a shortened URL by its slug")
    public String deleteUrl(
            @ToolParam(description = "the short slug to delete") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.warn("Tool deleteUrl userId={} slug={}", userId, slug);

        try {

            resilientUrlOps.delete(slug, userId);

        } catch (ResilientUrlOps.UrlServiceUnavailableException ex) {

            return "Could not delete '%s' - url-service temporarily unavailable.".formatted(slug);
        }

        log.info("deleteUrl completed userId={} slug={}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }
}