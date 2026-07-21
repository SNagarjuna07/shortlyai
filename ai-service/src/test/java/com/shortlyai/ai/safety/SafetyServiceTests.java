package com.shortlyai.ai.safety;

import com.shortlyai.ai.safety.dto.SafetyCheckRequest;
import com.shortlyai.ai.safety.dto.SafetyCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SafetyServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    private final Executor directExecutor = Runnable::run;

    private final Resource prompt = new ByteArrayResource("Check safety of {url}".getBytes(StandardCharsets.UTF_8));

    SafetyService safetyService;

    @BeforeEach
    void setUp() {
        safetyService = new SafetyService(chatClient, prompt, directExecutor);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void check_safeUrl_returnsSafeResponse() {

        SafetyCheckResponse response = new SafetyCheckResponse(true, "LOW", "Looks fine");
        when(callResponseSpec.entity(SafetyCheckResponse.class)).thenReturn(response);

        SafetyCheckResponse result = safetyService.check(new SafetyCheckRequest("https://example.com")).join();

        assertThat(result.safe()).isTrue();
    }

    @Test
    void check_unsafeUrl_returnsFlaggedResponse() {

        SafetyCheckResponse response = new SafetyCheckResponse(false, "HIGH", "Phishing pattern detected");
        when(callResponseSpec.entity(SafetyCheckResponse.class)).thenReturn(response);

        SafetyCheckResponse result = safetyService.check(new SafetyCheckRequest("https://bad.com")).join();

        assertThat(result.safe()).isFalse();
        assertThat(result.riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void check_llmReturnsNull_throwsIllegalState() {

        when(callResponseSpec.entity(SafetyCheckResponse.class)).thenReturn(null);

        assertThatThrownBy(() ->
                safetyService.check(new SafetyCheckRequest("https://example.com")).join()
        ).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void checkFallback_failsClosed_neverReportsafe() {

        SafetyCheckResponse fallback = safetyService.checkFallback(
                new SafetyCheckRequest("https://example.com"),
                new RuntimeException("down")
        ).join();

        assertThat(fallback.safe()).isFalse();
        assertThat(fallback.riskLevel()).isEqualTo("unknown");
    }
}