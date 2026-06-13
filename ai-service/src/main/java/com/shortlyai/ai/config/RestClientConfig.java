package com.shortlyai.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // bean name "urlServiceClient" -> matched via @Qualifier in tool classes
    @Bean
    RestClient urlServiceClient(
            @Value("${services.url-service.base-url}") String baseUrl
    ) {

        return RestClient
                .builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    RestClient analyticsServiceClient(
            @Value("${services.analytics-service.base-url}")
            String baseUrl
    ) {

        return RestClient
                .builder()
                .baseUrl(baseUrl)
                .build();
    }
}