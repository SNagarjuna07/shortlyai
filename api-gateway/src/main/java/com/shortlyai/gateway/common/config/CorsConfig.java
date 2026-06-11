package com.shortlyai.gateway.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

// Reactive CORS - CorsWebFilter (WebFlux) not CorsConfiguration (Servlet)
// Applied ONCE at gateway - no need to configure CORS in downstream services
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {

        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",  // React dev server
                "http://localhost:5173"   // Vite dev server
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // X-Trace-Id — let clients read the trace ID from responses (useful for debugging)
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));

        // headers
        config.setExposedHeaders(List.of(
                "X-Trace-Id",
                "X-RateLimit-Remaining",        // client knows how many left
                "X-RateLimit-Burst-Capacity",   // client knows max burst
                "Retry-After"                   // already set on 429
        )); // expose to browser JS

        config.setAllowCredentials(true); // allow Authorization header in CORS requests
        config.setMaxAge(3600L);          // browsers cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // apply to all paths

        return new CorsWebFilter(source);
    }
}