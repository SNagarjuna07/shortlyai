package com.shortlyai.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

// GlobalFilter - runs on EVERY route, no per-route config needed
// Order -2 - runs before JwtAuthFilter (-1) so trace ID is available in JWT logs
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    // Header name - same string must be used in all 5 services for MDC
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public int getOrder() {
        return -2; // lower number = higher priority, runs before JWT filter
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // Reuse existing trace ID if client or upstream sent one
        // Otherwise generate fresh UUID for this request lifecycle
        String traceId = exchange
                .getRequest()
                .getHeaders()
                .getFirst(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank() || traceId.length() > 100 || !traceId.matches("[a-zA-Z0-9-]+")) {

            traceId = UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;

        // Mutate request - adds X-Trace-Id header going TO downstream services
        // They read this and put it in their MDC for correlated logging
        ServerHttpRequest mutatedRequest = exchange
                .getRequest()
                .mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        // Mutate response - adds X-Trace-Id header going BACK to client
        exchange
                .getResponse()
                .getHeaders()
                .add(TRACE_ID_HEADER, finalTraceId);

        MDC.put("traceId", finalTraceId);

        log.debug("Trace ID assigned: {} for path: {}", finalTraceId,
                exchange.getRequest().getURI().getPath());

        // Pass mutated exchange downstream - all subsequent filters see the trace ID
        return chain.filter(
                        exchange
                                .mutate()
                                .request(mutatedRequest)
                                .build()
                )
                .doFinally(signalType -> MDC.remove("traceId"));
    }
}