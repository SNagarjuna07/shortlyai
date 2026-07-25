package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
import com.shortlyai.ai.websearch.WebSearchTool;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ClassificationService {

    private final ChatClient chatClient;

    private final WebSearchTool webSearchTool;

    private final Resource classifyPrompt;

    public ClassificationService(
            ChatClient chatClient,
            WebSearchTool webSearchTool,
            @Value("classpath:prompts/classification-service-prompt/classification-prompt.st")
            Resource classifyPrompt
    ) {
        this.chatClient = chatClient;
        this.webSearchTool = webSearchTool;
        this.classifyPrompt = classifyPrompt;
    }

    @CircuitBreaker(name = "classification", fallbackMethod = "classificationFallback")
    @Retry(name = "classification")
    @TimeLimiter(name = "classification")
    public CompletableFuture<ClassificationResponse> classify(ClassificationRequest classificationRequest) {

        return CompletableFuture.supplyAsync(() -> {

            ClassificationResponse result = chatClient.prompt()
                    .system(s ->
                            s.text(classifyPrompt)
                                    .param("url", classificationRequest.url())
                    )
                    .user("Classify this URL")
                    .tools(webSearchTool)
                    .call()
                    .entity(ClassificationResponse.class);

            log.info("Classified url: {} as: {}", classificationRequest.url(), result);

            return result;
        });
    }

    public CompletableFuture<ClassificationResponse> classificationFallback(ClassificationRequest request, Throwable t) {

        log.warn("Classification failed, url: {}, reason: {}", request.url(), t.getMessage());

        return CompletableFuture.completedFuture(
                new ClassificationResponse(request.url(), "Unknown", 0.0, List.of())
        );
    }
}