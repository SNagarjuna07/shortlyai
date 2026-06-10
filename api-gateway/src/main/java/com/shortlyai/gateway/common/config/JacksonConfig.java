package com.shortlyai.gateway.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfig {

    // JsonMapper not auto-configured in reactive gateway context
    // Define explicitly so GlobalErrorHandler can inject it
    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }
}