package com.shortlyai.ai.mcp.tools;

import com.shortlyai.ai.mcp.auth.McpUserContext;
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
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpUrlToolsTests {

    @Mock
    ResilientUrlOps resilientUrlOps;

    @Mock
    McpSyncRequestContext context;

    McpUrlTools mcpUrlTools;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        mcpUrlTools = new McpUrlTools(resilientUrlOps);
        McpUserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        McpUserContext.clear();
    }

    @Test
    void shortenUrl_noAuthenticatedUser_throwsIllegalState() {

        McpUserContext.clear(); // simulate filter misconfiguration

        assertThatThrownBy(() -> mcpUrlTools.shortenUrl("https://example.com", context))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortenUrl_elicitationEnabled_invalidUrlFormat_returnsGuardMessageWithoutCallingService() {

        when(context.elicitEnabled()).thenReturn(true);

        String result = mcpUrlTools.shortenUrl("not-a-url", context);

        assertThat(result).contains("does not look like a valid URL");
        verifyNoInteractions(resilientUrlOps);
    }

    @Test
    void shortenUrl_success_returnsFormattedShortUrl() {

        when(context.elicitEnabled()).thenReturn(false);

        UrlOperationsService.ShortenResult result =
                new UrlOperationsService.ShortenResult(7L, "abc123", "http://short.ly/abc123");

        when(resilientUrlOps.shorten("https://example.com", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(result));

        String response = mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(response)
                .contains("http://short.ly/abc123")
                .contains("urlId: 7")
                .contains("slug: abc123");
    }

    @Test
    void shortenUrl_circuitBreakerOpenReturnsNull_returnsFriendlyUnavailableMessage() {

        when(context.elicitEnabled()).thenReturn(false);

        when(resilientUrlOps.shorten("https://example.com", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        String response = mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(response).contains("temporarily unavailable");
    }

    @Test
    void shortenUrl_downstream4xx_returnsCleanMessageInsteadOfThrowing() {

        when(context.elicitEnabled()).thenReturn(false);

        HttpClientErrorException httpEx =
                HttpClientErrorException.create(HttpStatusCode.valueOf(400), "Bad Request", null, null, null);

        CompletableFuture<UrlOperationsService.ShortenResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(httpEx);

        when(resilientUrlOps.shorten("https://example.com", USER_ID)).thenReturn(failed);

        String response = mcpUrlTools.shortenUrl("https://example.com", context);

        assertThat(response).contains("Could not shorten URL");
    }

    @Test
    void shortenUrl_downstreamNon4xxFailure_rethrowsCompletionException() {

        when(context.elicitEnabled()).thenReturn(false);

        CompletableFuture<UrlOperationsService.ShortenResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("connection reset"));

        when(resilientUrlOps.shorten("https://example.com", USER_ID)).thenReturn(failed);

        assertThatThrownBy(() -> mcpUrlTools.shortenUrl("https://example.com", context))
                .isInstanceOf(CompletionException.class);
    }

    @Test
    void getUrlDetails_success_returnsFormattedDetails() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(3L, "myslug", "https://x.com", "http://short.ly/myslug", 42L);

        when(resilientUrlOps.getDetails("myslug", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));

        String response = mcpUrlTools.getUrlDetails("myslug");

        assertThat(response).contains("urlId: 3").contains("clicks: 42");
    }

    @Test
    void getUrlDetails_notFound_returnsUnavailableMessage() {

        when(resilientUrlOps.getDetails("ghost", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        String response = mcpUrlTools.getUrlDetails("ghost");

        assertThat(response).contains("temporarily unavailable");
    }

    @Test
    void deleteUrl_slugNotFound_returnsMessageWithoutAttemptingDelete() {

        when(resilientUrlOps.getDetails("ghost", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(null));

        String response = mcpUrlTools.deleteUrl("ghost", context);

        assertThat(response).contains("Could not find a URL");
        verify(resilientUrlOps, never()).delete(any(), any());
    }

    @Test
    void deleteUrl_noElicitationSupport_deletesDirectly() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(4L, "toDelete", "https://x.com", "http://short.ly/toDelete", 0L);

        when(resilientUrlOps.getDetails("toDelete", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));
        when(context.elicitEnabled()).thenReturn(false);
        when(resilientUrlOps.delete("toDelete", USER_ID)).thenReturn(CompletableFuture.completedFuture(true));

        String response = mcpUrlTools.deleteUrl("toDelete", context);

        assertThat(response).isEqualTo("Deleted URL with slug: toDelete");
        verify(resilientUrlOps).delete("toDelete", USER_ID);
    }

    @Test
    void deleteUrl_deleteCallFails_returnsUnavailableMessage() {

        UrlOperationsService.UrlDetails details =
                new UrlOperationsService.UrlDetails(4L, "toDelete", "https://x.com", "http://short.ly/toDelete", 0L);

        when(resilientUrlOps.getDetails("toDelete", USER_ID))
                .thenReturn(CompletableFuture.completedFuture(details));
        when(context.elicitEnabled()).thenReturn(false);
        when(resilientUrlOps.delete("toDelete", USER_ID)).thenReturn(CompletableFuture.completedFuture(false));

        String response = mcpUrlTools.deleteUrl("toDelete", context);

        assertThat(response).contains("temporarily unavailable");
    }
}