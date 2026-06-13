package com.shortlyai.ai.slug;

import com.shortlyai.ai.slug.dto.SlugRequest;
import com.shortlyai.ai.slug.dto.SlugResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlugService {

    private final ChatClient chatClient;

    public SlugResponse suggest(SlugRequest request) {

        log.info("Generating slug suggestions for url: {}", request.url());

        String prompt = """
                Suggest 5 short URL slugs for a link shortener.
                Rules: lowercase letters, numbers, hyphens only, max 20 chars,
                memorable, related to the URL/context below.

                URL: %s
                Context: %s

                Return only the slugs, no explanation.
                """.formatted(request.url(), request.context() == null ? "none" : request.context());

        SlugResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(SlugResponse.class);  // Spring-AI converts JSON response to this DTO

        log.debug("Slug suggestions for url: {}: {}", request.url(), response.suggestions());

        return response;
    }
}