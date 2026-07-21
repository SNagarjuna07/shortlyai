package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
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
class ClassificationServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    private final Executor directExecutor = Runnable::run;

    private final Resource prompt = new ByteArrayResource("Classify {url}".getBytes(StandardCharsets.UTF_8));

    ClassificationService classificationService;

    @BeforeEach
    void setUp() {
        classificationService = new ClassificationService(chatClient, prompt, directExecutor);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void classify_success_returnsLlmResponse() {

        ClassificationResponse response = new ClassificationResponse("Title", "tech", 0.9, java.util.List.of("java"));
        when(callResponseSpec.entity(ClassificationResponse.class)).thenReturn(response);

        ClassificationResponse result = classificationService.classify(
                new ClassificationRequest("https://example.com")
        ).join();

        assertThat(result).isEqualTo(response);
    }

    @Test
    void classify_llmReturnsNull_throwsIllegalState() {

        when(callResponseSpec.entity(ClassificationResponse.class)).thenReturn(null);

        assertThatThrownBy(() ->
                classificationService.classify(new ClassificationRequest("https://example.com")).join()
        ).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void classifyFallback_returnsUnknownCategory() {

        ClassificationResponse fallback = classificationService.classifyFallback(
                new ClassificationRequest("https://example.com"),
                new RuntimeException("down")
        ).join();

        assertThat(fallback.category()).isEqualTo("uncategorized");
        assertThat(fallback.confidence()).isEqualTo(0.0);
    }
}