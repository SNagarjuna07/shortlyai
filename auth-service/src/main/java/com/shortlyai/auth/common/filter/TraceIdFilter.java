package com.shortlyai.auth.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Read trace ID gateway stamped — fallback if service called directly (e.g. dev)
        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {

            traceId = UUID.randomUUID().toString();
        }

        // Put into MDC - Logback pattern %mdc{traceId} now resolves correctly
        MDC.put("traceId", traceId);

        // Echo back in response so client has it

        response.addHeader(TRACE_ID_HEADER, traceId);

        try {

            filterChain.doFilter(request, response); // continue chain

        } finally {

            MDC.remove("traceId"); // ALWAYS clean up — thread goes back to pool
        }
    }
}