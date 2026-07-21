package com.shortlyai.ai.mcp.tools;

import com.shortlyai.ai.mcp.auth.McpUserContext;
import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpAnalyticsToolsTests {

    @Mock
    ResilientAnalyticsOps resilientAnalyticsOps;

    @Mock
    ResilientUrlOps resilientUrlOps;

    McpAnalyticsTools mcpAnalyticsTools;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {

        mcpAnalyticsTools = new McpAnalyticsTools(resilientAnalyticsOps, resilientUrlOps);
        McpUserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        McpUserContext.clear();
    }

    @Test
    void getUrlStats_success_returnsFormattedMessage() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(1L, "abc123", "https://example.com", "http://short.ly/abc123", 0L);

        when(resilientUrlOps.getDetails("abc123", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        when(resilientAnalyticsOps.getStats(1L, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        new AnalyticsOperationsService.StatsResult(1L, 42L, "ok")
                ));

        String result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(result).contains("abc123").contains("42");
    }

    @Test
    void getUrlStats_slugNotFound_returnsNotFoundMessage() {

        when(resilientUrlOps.getDetails("missing", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        String result = mcpAnalyticsTools.getUrlStats("missing");

        assertThat(result).contains("Could not find");
    }

    @Test
    void getUrlStats_statsUnavailable_returnsUnavailableMessage() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(1L, "abc123", "https://example.com", "http://short.ly/abc123", 0L);

        when(resilientUrlOps.getDetails("abc123", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        when(resilientAnalyticsOps.getStats(1L, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        String result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(result).contains("temporarily unavailable");
    }

    @Test
    void getUrlStats_httpClientError_returnsCleanMessage() {

        HttpClientErrorException httpEx = HttpClientErrorException.create(
                org.springframework.http.HttpStatus.NOT_FOUND, "Not Found",
                null, null, null
        );

        when(resilientUrlOps.getDetails("abc123", USER_ID))
                .thenThrow(new CompletionException(httpEx));

        String result = mcpAnalyticsTools.getUrlStats("abc123");

        assertThat(result).contains("Could not retrieve stats");
    }

    @Test
    void getTopUrls_failure_returnsUnavailableMessage() {

        when(resilientAnalyticsOps.getTopUrls(5, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        String result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(result).contains("temporarily unavailable");
    }

    @Test
    void getTopUrls_noData_returnsNoDataMessage() {

        when(resilientAnalyticsOps.getTopUrls(5, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        String result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(result).contains("don't have any URLs");
    }

    @Test
    void getTopUrls_withData_returnsFormattedList() {

        when(resilientAnalyticsOps.getTopUrls(5, USER_ID))
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(new AnalyticsOperationsService.TopUrlResult(1L, 99L))
                ));

        String result = mcpAnalyticsTools.getTopUrls(5);

        assertThat(result).contains("urlId 1").contains("99 clicks");
    }
}