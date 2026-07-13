package com.shortlyai.ai.slug;

import com.shortlyai.ai.slug.dto.SlugRequest;
import com.shortlyai.ai.slug.dto.SlugResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class SlugService {

    private final ChatClient chatClient;

    private final Resource slugPrompt;

    public SlugService(
            ChatClient chatClient,
            @Value("classpath:prompts/slug-service-prompt/slug-prompt.st")
            Resource slugPrompt
    ) {
        this.chatClient = chatClient;
        this.slugPrompt = slugPrompt;
    }

    public SlugResponse suggest(SlugRequest request) {

        log.info("Generating slug suggestions for url: {}", request.url());

        PromptTemplate template = new PromptTemplate(slugPrompt);

        String prompt = template
                .render(
                        Map.of(
                                "url", request.url(),
                                "context", request.context() == null ? "none" : request.context()
                        )
                );

        SlugResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(SlugResponse.class);  // Spring-AI converts JSON response to this DTO

        log.debug("Slug suggestions for url: {}: {}", request.url(), response.suggestions());

        return response;
    }
}