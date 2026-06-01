package com.shortlyai.auth.authentication;

import com.shortlyai.auth.common.exception.InvalidCredentialsException;
import com.shortlyai.auth.dto.AuthResponse;
import com.shortlyai.auth.dto.LoginRequest;
import com.shortlyai.auth.security.JwtUtil;
import com.shortlyai.auth.user.User;
import com.shortlyai.auth.user.UserMapper;
import com.shortlyai.auth.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service // Spring registers this as the AuthService bean
@Transactional // every public method runs inside a transaction by default
@RequiredArgsConstructor // Lombok generates the constructor for bean injection
public class AuthServiceImpl implements AuthService {

    // Constructor injection — all dependencies declared as final
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;


    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        // Step 1 — find user by email, fail fast if not found
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed — email not found: {}", request.email());
                    // Generic message — never tell caller whether email or password was wrong
                    return new InvalidCredentialsException("Invalid email or password");
                });

        // Step 2 — verify raw password against BCrypt hash in DB
        // BCrypt.matches() is intentionally slow — brute force protection
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Login failed — wrong password for email: {}", request.email());
            throw new InvalidCredentialsException("Invalid email or password");         }

        // Step 3 — generate both tokens
        // role stored as claim — gateway reads it without hitting DB
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );

        log.info("Login successful for userId: {}", user.getId());

        // Step 4 — build and return response
        return new AuthResponse(accessToken, refreshToken, userMapper.toResponse(user));
    }
}