package com.shortlyai.ai.safety;

import com.shortlyai.ai.safety.dto.SafetyCheckRequest;
import com.shortlyai.ai.safety.dto.SafetyCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyService {

    private final ChatClient chatClient;

    public SafetyCheckResponse check(SafetyCheckRequest request) {

        log.info("Running safety check for url: {}", request.url());

        // Note: text-only heuristic check (no live browsing/scanning)
        String prompt = """
                Analyze this URL for signs of phishing, scams, or malware
                based on its structure (domain, TLD, subdomains, suspicious
                keywords like 'login', 'verify', 'free', IP-based domains, etc).

                URL: %s

                Return: safe (true/false), riskLevel (LOW/MEDIUM/HIGH),
                and a one-sentence reasoning.
                """.formatted(request.url());

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