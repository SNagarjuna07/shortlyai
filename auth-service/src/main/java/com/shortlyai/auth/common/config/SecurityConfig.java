package com.shortlyai.auth.common.config;

import com.shortlyai.auth.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration // tells Spring: this class produces beans
@RequiredArgsConstructor
public class SecurityConfig {


    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // CSRF — Cross Site Request Forgery protection
        // Only needed for browser session-based apps (cookies)
        // JWT APIs are stateless — no cookies, no CSRF risk — safe to disable
        http.csrf(AbstractHttpConfigurer::disable)

                // Sessions — tell Spring never to create an HttpSession
                // Every request must carry its own JWT — no server-side session state
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules — evaluated top to bottom, first match wins
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/api/v1/auth/**").permitAll()
                                .anyRequest().authenticated())

                // Wire in JwtAuthFilter — runs BEFORE Spring's default auth filter
                // Order matters — JWT must be validated first so SecurityContext is populated
                // when Spring's filter runs its own checks
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // CORS — allows frontend to call this API from a different origin
                // Without this, browser blocks all cross-origin requests
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    // Allowed origins — localhost for dev, real domain for prod
                    // Never use * in prod — too permissive
                    config.setAllowedOrigins(List.of(
                            "http://localhost:3000",   // React dev server
                            "http://localhost:5173"    // Vite dev server
                    ));

                    // Allowed HTTP methods
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

                    // Allowed headers — Authorization carries JWT, Content-Type for JSON body
                    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

                    // Allow cookies/auth headers in cross-origin requests
                    config.setAllowCredentials(true);

                    return config;
                }));

        return http.build();

    }

    // BCrypt — industry standard for password hashing
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}