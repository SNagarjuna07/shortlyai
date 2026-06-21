package com.shortlyai.analytics.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // No need of ID for actuators and Swagger
        if (uri.startsWith("/actuator")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3")
        ) {

            filterChain.doFilter(request, response);

            return;
        }

        String userId = request.getHeader("X-User-Id");

        // check if userId is missing
        if (userId == null || userId.isBlank()) {

            response.setStatus(401);

            response.setCharacterEncoding("UTF-8");

            response.setContentType("application/json");

            response.getWriter().write(""" 
                    {"error":"Missing X-User-Id header"}
                    """);

            log.warn("Missing X-User-Id on: {}", request.getRequestURI());

            return;
        }

        try {
            // extract ID
            UUID parsedUserId = UUID.fromString(userId);

            // Standard Spring Security way to represent an authenticated user
            // Principal = UUID userId, credentials = null, role = USER
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            parsedUserId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            // Store in SecurityContext — .authenticated() check reads this
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Authenticated userId: {}", parsedUserId);

            // continue filter
            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {

            log.warn("Invalid X-User-Id format on {}: {}", request.getRequestURI(), userId);

            response.setStatus(401);

            response.setContentType("application/json");

            response.setCharacterEncoding("UTF-8");

            response.getWriter().write("""
                    {"error":"Invalid X-User-Id header"}
                    """);

        } finally {

            SecurityContextHolder.clearContext();
        }
    }
}
