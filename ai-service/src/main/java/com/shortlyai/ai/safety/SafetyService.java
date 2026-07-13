package com.shortlyai.ai.safety;

import com.shortlyai.ai.safety.dto.SafetyCheckRequest;
import com.shortlyai.ai.safety.dto.SafetyCheckResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class SafetyService {

    private final ChatClient chatClient;

    private final Resource safetyPrompt;

    public SafetyService(
            ChatClient chatClient,
            @Value("classpath:prompts/safety-service-prompt/safety-prompt.st")
            Resource safetyPrompt
    ) {
        this.chatClient = chatClient;
        this.safetyPrompt = safetyPrompt;
    }

    public SafetyCheckResponse check(SafetyCheckRequest request) {

        log.info("Running safety check for url: {}", request.url());

        PromptTemplate template = new PromptTemplate(safetyPrompt);

        String prompt = template
                .render(
                        Map.of("url", request.url())
                );

        SafetyCheckResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(SafetyCheckResponse.class);

        if (response.safe()) {

            log.debug("Safety check passed url: {}, riskLevel: {}",
                    request.url(), response.riskLevel());

        } else {

            log.warn("Safety check FLAGGED url: {}, riskLevel: {}, reason: {}",
                    request.url(), response.riskLevel(), response.reasoning());
        }

        return response;
    }
}