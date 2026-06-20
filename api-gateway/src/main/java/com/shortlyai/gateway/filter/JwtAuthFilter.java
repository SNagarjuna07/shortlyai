package com.shortlyai.gateway.filter;

import com.shortlyai.gateway.dto.ErrorResponse;
import com.shortlyai.gateway.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;

// GlobalFilter — runs on every request, before routing to any service
// Order -1 — runs after TraceIdFilter (-2), before all other filters
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    // JsonMapper - serializes ErrorResponse to JSON bytes for 401 responses
    private final JsonMapper jsonMapper;

    private final String apiPrefix;

    public JwtAuthFilter(
            JwtUtil jwtUtil,
            JsonMapper jsonMapper,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.jwtUtil = jwtUtil;
        this.jsonMapper = jsonMapper;
        this.apiPrefix = apiPrefix;
    }

    @Override
    public int getOrder() {
        return -1; // runs after TraceIdFilter (-2)
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {

        String path = exchange
                .getRequest()
                .getURI()
                .getPath();

        // Skip JWT check for public paths
        if (isPublicPath(path)) {

            log.debug("Public path, skipping JWT check: {}", path);

            return chain.filter(exchange);
        }

        // Extract Authorization header - must be "Bearer <token>"
        String authHeader = exchange
                .getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        // Missing or malformed header - reject immediately
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            log.warn("Missing or invalid Authorization header for path: {}", path);

            return writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Authorization header required"
            );
        }

        // Strip "Bearer " prefix - 7 chars
        String token = authHeader.substring(7);

        // Validate signature + expiry
        if (!jwtUtil.isTokenValid(token)) {

            log.warn("Invalid or expired JWT for path: {}", path);

            return writeError(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token"
            );
        }

        // Token valid - extract claims to inject as headers for downstream services
        String userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);

        // WHY inject headers?
        // Downstream services (url-service, analytics-service) need userId
        // But we don't want each service to re-parse and re-validate the JWT
        // Gateway validates ONCE, downstream services trust X-User-Id header
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)      // e.g. "550e8400-e29b-41d4-a716-446655440000"
                .header("X-User-Role", role)      // e.g. "ROLE_PRO"
                .build();

        log.debug("JWT valid, userId: {}, role: {}, forwarding to: {}", userId, role, path);

         // Pass mutated exchange - downstream services see the injected headers
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    // Decides if a path requires JWT or not
    private boolean isPublicPath(String path) {

        // All auth endpoints are public
        if (path.startsWith(apiPrefix + "/auth/")) return true;

        // OAuth2 callback - Spring Security handles these internally in auth-service
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) return true;

        if (path.equals("/mcp") || path.startsWith("/mcp/")) return true;

        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;

        // Short URL redirect endpoint
        return path.startsWith("/r/");
    }

    // Writes a JSON error response directly - request never reaches downstream service
    // reactive pattern: wrap bytes in DataBuffer, write to response body
    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                exchange.getRequest().getURI().getPath(),
                Instant.now()
        );

        try {

            // Serialize ErrorResponse -> JSON bytes -> DataBuffer
            byte[] bytes = jsonMapper.writeValueAsBytes(error);

            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer));

        } catch (Exception e) {

            log.error("Failed to serialize error response", e);

            return response.setComplete(); // send empty response rather than hang
        }
    }
}