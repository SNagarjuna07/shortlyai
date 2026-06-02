package com.shortlyai.auth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// Configures Redis template — used to read/write refresh tokens
@Configuration
public class RedisConfig {

    // StringRedisTemplate — simplified template for String key-value pairs
    // RefreshTokens are strings — no need for generic RedisTemplate
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
