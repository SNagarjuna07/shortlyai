package com.shortlyai.url.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// Dedicated executor for redirect-path side effects (click-count write,
// click event publish)
@Configuration
public class AsyncTrackingConfig {

    @Bean
    public Executor clickTrackingExecutor() {

        return Executors.newVirtualThreadPerTaskExecutor();
    }
}