package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationService {

    private final ChatClient chatClient;

    public ClassificationResponse classify(ClassificationRequest request) {

        log.info("Classifying URL: {}", request.url());

        String prompt = """
                You are a URL classification assistant for a URL shortener.
                
                Generate a short, descriptive title (max 60 chars) for this URL,
                as if it were the webpage's <title> tag - based on the URL alone.
                
                Classify this URL into ONE category from:
                SOCIAL_MEDIA, NEWS, ECOMMERCE, ENTERTAINMENT, EDUCATION,
                TECHNOLOGY, FINANCE, GOVERNMENT, ADULT, MALICIOUS, OTHER.
                
                Also give a confidence score (0.0 to 1.0) and up to 3
                short topic tags describing likely content.
                
                URL: %s
                """.formatted(request.url());

        // .entity() = Spring AI auto-converts model JSON output into this record
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