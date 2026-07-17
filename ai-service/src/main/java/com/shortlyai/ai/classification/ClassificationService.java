package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ClassificationService {

    private final ChatClient chatClient;

    private final Resource classificationPrompt;

    private final Executor resilientOpsExecutor;

    public ClassificationService(
            ChatClient chatClient,
            @Value("classpath:prompts/classification-service-prompt/classification-prompt.st")
            Resource classificationPrompt,
            @Qualifier("resilientOpsExecutor") Executor resilientOpsExecutor
    ) {
        this.chatClient = chatClient;
        this.classificationPrompt = classificationPrompt;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @CircuitBreaker(name = "ai-service", fallbackMethod = "classifyFallback")
    @Retry(name = "ai-service")
    @TimeLimiter(name = "ai-service")
    public CompletableFuture<ClassificationResponse> classify(ClassificationRequest request) {

        return CompletableFuture.supplyAsync(() -> {

            log.info("Classifying URL: {}", request.url());

            PromptTemplate template = new PromptTemplate(classificationPrompt);

            String prompt = template.render(Map.of("url", request.url()));

            ClassificationResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(ClassificationResponse.class);

            if (response == null) {

                throw new IllegalStateException("LLM returned unparseable response for classification");
            }

            log.debug(
                    "Classification result url: {}, category: {}, confidence: {}",
                    request.url(), response.category(), response.confidence()
            );

            return response;

        }, resilientOpsExecutor);
    }

    public CompletableFuture<ClassificationResponse> classifyFallback(ClassificationRequest request, Throwable ex) {

        log.error("Classification unavailable for url: {}", request.url(), ex);

        return CompletableFuture.completedFuture(
                new ClassificationResponse(
                        "Unknown",
                        "uncategorized",
                        0.0,
                        List.of()
                )
        );
    }
}