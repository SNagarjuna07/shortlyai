package com.shortlyai.auth.common.config;

import com.shortlyai.auth.oauth2.OAuth2SuccessHandler;
import com.shortlyai.auth.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration // tells Spring: this class produces beans
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    private final String apiPrefix;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.apiPrefix = apiPrefix;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // CSRF — Cross Site Request Forgery protection
        // Only needed for browser session-based apps (cookies)
        // JWT APIs are stateless — no cookies, no CSRF risk — safe to disable
        http.csrf(AbstractHttpConfigurer::disable)

                // disabling form login
                .formLogin(AbstractHttpConfigurer::disable)

                // Sessions — tell Spring never to create an HttpSession
                // Every request must carry its own JWT — no server-side session state
                // IF_REQUIRED because OAuth 2 saves state after redirecting
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // Authorization rules — evaluated top to bottom, first match wins
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(apiPrefix + "/auth/me").authenticated()
                                .requestMatchers(apiPrefix + "/auth/**", "/actuator/**").permitAll()
                                .requestMatchers(apiPrefix + "/auth/apikeys/**").authenticated()
                                .anyRequest().authenticated())

                // Wire in JwtAuthFilter — runs BEFORE Spring's default auth filter
                // Order matters — JWT must be validated first so SecurityContext is populated
                // when Spring's filter runs its own checks
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // OAuth config
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                );

        return http.build();

    }

    // BCrypt — industry standard for password hashing
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}