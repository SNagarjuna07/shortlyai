package com.shortlyai.gateway.ratelimit;

import com.shortlyai.gateway.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

// Custom GlobalFilter — replaces RequestRateLimiter default-filter in yaml
// Gives us: per-service rate configs + proper 429 JSON body
// Order 1: runs AFTER JwtAuthFilter (-1), so X-User-Id is already injected
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter redisRateLimiter; // bean from RateLimitConfig
    private final KeyResolver userKeyResolver;        // @Primary bean from RateLimitConfig
    private final JsonMapper jsonMapper;

    @Override
    public int getOrder() {
        return 1; // after JWT (-1), before routing
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();
        String routeId = resolveRouteId(path); // which service bucket config to use

        return userKeyResolver.resolve(exchange)
                .flatMap(userKey -> {

                    // CRITICAL: prefix routeId to userKey
                    // Without this: auth and url share same bucket for same user
                    // With this: "auth:user:uuid" ≠ "url:user:uuid" → separate buckets
                    String bucketKey = userKey;

                    log.debug("Rate check for route: {}, bucket: {}", routeId, bucketKey);

                    // isAllowed(routeId, bucketKey):
                    //   routeId   -> picks Config (replenishRate, burstCapacity) from the map
                    //   bucketKey -> Redis key suffix for token/timestamp storage
                    return redisRateLimiter.isAllowed(routeId, bucketKey);
                })
                .flatMap(response -> {

                    if (response.isAllowed()) {
                        return chain.filter(exchange);
                    }

                    log.warn("Rate limit hit for route: {}, path: {}", routeId, path);

                    return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded for " + routeId);
                })
                .onErrorResume(ex -> {

                    // Fail open - Redis down should not block all traffic
                    log.error("Rate limiter error on route: {}, failing open: {}", routeId, ex.getMessage());

                    return chain.filter(exchange);
                });
    }

    // Maps request path -> routeId -> which rate config to apply
    // routeId must match keys in rate-limit.routes yaml (and in the bean's config map)
    private String resolveRouteId(String path) {

        if (path.startsWith("/api/v1/auth"))      return "auth";       // strict - brute force target
        if (path.startsWith("/api/v1/urls"))       return "url";        // moderate
        if (path.startsWith("/api/v1/analytics")) return "analytics";  // lenient - read-heavy
        if (path.startsWith("/api/v1/ai"))         return "ai";         // very strict - expensive ops
        if (path.startsWith("/r/"))                return "url";        // redirect = same bucket as url-service

        return "default"; // catch-all - uses constructor fallback config
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(status);

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        response.getHeaders().add("Retry-After", "1"); // tell client: retry after 1 sec

        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                exchange.getRequest().getURI().getPath(),
                Instant.now()
        );

        try {

            byte[] bytes = jsonMapper.writeValueAsBytes(error);

            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer));

        } catch (Exception e) {

            log.error("Failed to serialize rate limit error", e);

            return response.setComplete();
        }
    }
}