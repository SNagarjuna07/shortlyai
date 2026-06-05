package com.shortlyai.url.common.security;

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

// OncePerRequestFilter guarantees this runs exactly once per HTTP request
// Spring's filter chain can call filters multiple times in some dispatch scenarios
// — this base class prevents that
@Component
@Slf4j
public class HeaderAuthFilter extends OncePerRequestFilter {

    // Gateway always passes this header when user is authenticated
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Read the userId injected by the gateway after JWT validation
        String userId = request.getHeader(USER_ID_HEADER);

        // Null check gates everything — public endpoints won't have this header
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                // Parse String → Long — service layer expects Long
                Long parsedUserId = Long.parseLong(userId);

                // Standard Spring Security way to represent an authenticated user
                // Principal = Long userId, credentials = null, role = USER
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                parsedUserId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                // Store in SecurityContext — .authenticated() check reads this
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("Authenticated userId={}", parsedUserId);

            } catch (NumberFormatException e) {
                // Header present but not a valid Long — gateway bug or tampering
                // No auth set — request hits .authenticated() and gets 403
                log.warn("Invalid X-User-Id header value: {}", userId);
            }

        } else if (userId == null) {
            // Normal for public endpoints
            log.warn("No X-User-Id header on request: {}", request.getRequestURI());
        }

        // Always continue — filter sets context only, never blocks directly
        filterChain.doFilter(request, response);
    }
}