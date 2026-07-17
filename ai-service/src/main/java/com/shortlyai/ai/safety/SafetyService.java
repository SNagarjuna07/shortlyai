package com.shortlyai.ai.safety;

import com.shortlyai.ai.safety.dto.SafetyCheckRequest;
import com.shortlyai.ai.safety.dto.SafetyCheckResponse;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SafetyService {

    private final ChatClient chatClient;

    private final Resource safetyPrompt;

    private final Executor resilientOpsExecutor;

    public SafetyService(
            ChatClient chatClient,
            @Value("classpath:prompts/safety-service-prompt/safety-prompt.st")
            Resource safetyPrompt,
            @Qualifier("resilientOpsExecutor") Executor resilientOpsExecutor
    ) {
        this.chatClient = chatClient;
        this.safetyPrompt = safetyPrompt;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @CircuitBreaker(name = "ai-service", fallbackMethod = "checkFallback")
    @Retry(name = "ai-service")
    @TimeLimiter(name = "ai-service")
    public CompletableFuture<SafetyCheckResponse> check(SafetyCheckRequest request) {

        return CompletableFuture.supplyAsync(() -> {

            log.info("Running safety check for url: {}", request.url());

            PromptTemplate template = new PromptTemplate(safetyPrompt);

            String prompt = template.render(Map.of("url", request.url()));

            SafetyCheckResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(SafetyCheckResponse.class);

            if (response == null) {
                throw new IllegalStateException("LLM returned unparseable response for safety check");
            }

            if (response.safe()) {

                log.debug("Safety check passed url: {}, riskLevel: {}", request.url(), response.riskLevel());

            } else {

                log.warn("Safety check FLAGGED url: {}, riskLevel: {}, reason: {}",
                        request.url(), response.riskLevel(), response.reasoning());
            }

            return response;

        }, resilientOpsExecutor);
    }

    public CompletableFuture<SafetyCheckResponse> checkFallback(SafetyCheckRequest request, Throwable ex) {

        log.error("Safety check unavailable for url: {}", request.url(), ex);

        // fail CLOSED - "unknown" must never be reported as "safe"
        return CompletableFuture.completedFuture(
                new SafetyCheckResponse(
                        false,
                        "unknown",
                        "Safety check temporarily unavailable"
                )
        );
    }
}