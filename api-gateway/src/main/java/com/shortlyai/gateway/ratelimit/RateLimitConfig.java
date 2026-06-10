package com.shortlyai.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;
import java.net.InetSocketAddress;

// Configures Spring Cloud Gateway's built-in Redis rate limiter
// Uses token bucket algorithm - tokens fill up at replenishRate, burst allows spikes
@Configuration
public class RateLimitConfig {

    // RedisRateLimiter - Spring Cloud Gateway's built-in token bucket via Redis
    // replenishRate: 5  -> 5 new tokens added per second (sustained rate)
    // burstCapacity:  10  -> max tokens bucket can hold (spike tolerance)
    // requestedTokens: 1  -> each request costs 1 token
    // Effect: 5 req/sec normally, up to 10 in a burst, then throttled with 429
    @Bean
    public RedisRateLimiter redisRateLimiter() {

        return new RedisRateLimiter(5, 10, 1);
    }

    // KeyResolver - determines WHICH bucket to use for a given request
    // @Primary - marks this as the default resolver when multiple exist
    // Strategy:
    //   Authenticated user -> bucket per userId (fair per-user limiting)
    //   Anonymous request -> bucket per IP (handles unauthenticated endpoints)
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {

        return exchange -> {

            // X-User-Id injected by JwtAuthFilter - present on authenticated requests
            String userId = exchange
                    .getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            if (userId != null && !userId.isBlank()) {

                // "user:" prefix - avoids key collision with IP-based keys in Redis
                return Mono.just("user:" + userId);
            }

            // Anonymous request - rate limit by IP address
            // Handles /auth/login brute force, slug redirects, etc.
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();

            String ip = (remoteAddress != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown";

            return Mono.just("ip:" + ip);
        };
    }
}