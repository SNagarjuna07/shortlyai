package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ClassificationService {

    private final ChatClient chatClient;

    private final Resource classificationPrompt;

    public ClassificationService(
            ChatClient chatClient,
            @Value("classpath:prompts/classification-service-prompt/classification-prompt.st")
            Resource classificationPrompt
    ) {
        this.chatClient = chatClient;
        this.classificationPrompt = classificationPrompt;
    }

    public ClassificationResponse classify(ClassificationRequest request) {

        log.info("Classifying URL: {}", request.url());

        PromptTemplate template = new PromptTemplate(classificationPrompt);

        String prompt = template
                .render(
                        Map.of("url", request.url())
                );

        ClassificationResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(ClassificationResponse.class);

        log.debug(
                "Classification result url: {}, category: {}, confidence: {}",
                request.url(), response.category(), response.confidence()
        );

        return response;
    }
}