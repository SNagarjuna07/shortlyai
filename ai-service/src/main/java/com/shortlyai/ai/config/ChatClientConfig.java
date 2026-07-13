package com.shortlyai.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class ChatClientConfig {

    private final Resource systemPrompt;

    public ChatClientConfig(
            @Value("classpath:prompts/base-prompt/system.st")
            Resource systemPrompt
    ) {
        this.systemPrompt = systemPrompt;
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}