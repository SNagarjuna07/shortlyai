package com.shortlyai.auth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// @EnableAsync — activates @Async processing in this service
// Without this, @Async is silently ignored — methods run synchronously
@Configuration
@EnableAsync
public class AsyncConfig {

    // Custom thread pool for @Async tasks (audit writes)
    // Never use default SimpleAsyncTaskExecutor — it creates unbounded threads
    @Bean(name = "auditExecutor")
    @Primary // Spring uses default for unspecified async-beans
    public Executor auditExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);        // 2 threads always alive for audit writes
        executor.setMaxPoolSize(5);         // burst up to 5 under heavy login traffic
        executor.setQueueCapacity(100);     // queue 100 audit tasks before rejecting
        executor.setThreadNamePrefix("audit-exec-"); // visible in logs/thread dumps
        executor.initialize();

        return executor;
    }

    // Custom thread pool for @Async tasks (sending mail)
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);        // 2 threads always alive for sending mail
        executor.setMaxPoolSize(5);         // burst up to 5 under heavy registration traffic
        executor.setQueueCapacity(100);     // queue 100 audit tasks before rejecting
        executor.setThreadNamePrefix("email-exec-"); // visible in logs/thread dumps
        executor.initialize();

        return executor;
    }
}