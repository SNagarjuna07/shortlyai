package com.shortlyai.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class AsyncOpsConfig {

    @Bean(name = "resilientOpsExecutor")
    public Executor resilientOpsExecutor() {

        ThreadFactory factory = Thread.ofVirtual()
                .name("resilient-ops-", 0)
                .factory();

        return Executors.newThreadPerTaskExecutor(factory);
    }
}