package com.shortlyai.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncOpsConfig {

    @Bean(name = "resilientOpsExecutor")
    public Executor resilientOpsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}