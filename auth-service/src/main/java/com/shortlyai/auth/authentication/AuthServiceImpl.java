package com.shortlyai.auth.authentication;

import com.shortlyai.auth.common.exception.EmailAlreadyExistsException;
import com.shortlyai.auth.common.exception.InvalidCredentialsException;
import com.shortlyai.auth.common.exception.InvalidTokenException;
import com.shortlyai.auth.common.exception.UserNotFoundException;
import com.shortlyai.auth.dto.AuthResponse;
import com.shortlyai.auth.dto.LoginRequest;
import com.shortlyai.auth.dto.RefreshTokenRequest;
import com.shortlyai.auth.dto.RegisterRequest;
import com.shortlyai.auth.security.JwtUtil;
import com.shortlyai.auth.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

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
    private final RefreshTokenService refreshTokenService;


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
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Step 3 — generate both tokens
        // role stored as claim — gateway reads it without hitting DB
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );

        log.info("Login successful for userId: {}", user.getId());

        // Store refresh token in Redis — enables revocation on logout
        refreshTokenService.store(refreshToken, user.getId().toString());

        // Step 4 — build and return response
        return new AuthResponse(accessToken, refreshToken, userMapper.toResponse(user));
    }

    @Override
    public AuthResponse register(RegisterRequest registerRequest) {

        log.info("Register attempt for email: {}", registerRequest.email());

        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyExistsException("An account exists already with this email");
        }

        User user = new User();

        user.setName(registerRequest.name());
        user.setEmail(registerRequest.email());
        user.setPassword(passwordEncoder.encode(registerRequest.password()));
        user.setRole(Role.ROLE_FREE);
        user.setProvider(Provider.LOCAL);
        user.setVerified(false);

        User savedUser = userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(
                savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name()
        );
        String refreshToken = jwtUtil.generateRefreshToken(
                savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name()
        );

        log.info("Registration successful for userId: {}", savedUser.getId());

        // Store refresh token in Redis — enables revocation on logout
        refreshTokenService.store(refreshToken, savedUser.getId().toString());

        return new AuthResponse(accessToken, refreshToken, userMapper.toResponse(savedUser));
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {

        // Check token signature and expiry
        if (!jwtUtil.isTokenValid(request.refreshToken())) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        // Checking if it exists or not
        if (!refreshTokenService.exists(request.refreshToken())) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        // Generating new access and refresh tokens
        String id = jwtUtil.extractUserId(request.refreshToken());
        String email = jwtUtil.extractEmail(request.refreshToken());
        String role = jwtUtil.extractRole(request.refreshToken());

        String accessToken = jwtUtil.generateAccessToken(UUID.fromString(id), email, role);

        String refreshToken = jwtUtil.generateRefreshToken(UUID.fromString(id), email, role);

        // Fetch user before Redis so that if DB throws, data in Redis won't be corrupted (fail-fast)
        User user = userRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Deleting old refresh token
        refreshTokenService.delete(request.refreshToken());

        // Storing new refresh token generated
        refreshTokenService.store(refreshToken, id);

        log.info("New refresh token generated for userId: {}", id);

        return new AuthResponse(accessToken, refreshToken, userMapper.toResponse(user));
    }
}