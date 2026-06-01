package com.shortlyai.auth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration // tells Spring: this class produces beans
public class SecurityConfig {

    // BCrypt — industry standard for password hashing
    // strength 12 — work factor, higher = slower = harder to brute force
    // default is 10, 12 is production standard
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}