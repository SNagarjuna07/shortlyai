package com.shortlyai.auth.authentication;

import com.shortlyai.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

// Handles all Redis operations for refresh tokens
// Separated from AuthServiceImpl — single responsibility
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redis;
    private final JwtUtil jwtUtil;

    // Key prefix — namespaces refresh tokens in Redis
    // Prevents key collisions with other Redis data (rate limits, cache, etc.)
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";


    // Store refresh token in Redis with TTL matching token expiry
    // Key: "refresh:<token>" — Value: userId string
    public void store(String refreshToken, String userId) {
        // TTL = time between now and token expiry — Redis auto-deletes after this
        Instant expiry = jwtUtil.extractExpiry(refreshToken);
        long ttlSeconds = Duration.between(Instant.now(), expiry).getSeconds();

        redis.opsForValue().set(
                REFRESH_TOKEN_PREFIX + refreshToken,  // key
                userId,                                // value
                Duration.ofSeconds(ttlSeconds)         // TTL — auto-expires
        );

        log.debug("Stored refresh token in Redis for userId: {}", userId);
    }

    // Returns true if token exists in Redis — false means revoked or expired
    public boolean exists(String refreshToken) {
        return Boolean.TRUE.equals(
                redis.hasKey(REFRESH_TOKEN_PREFIX + refreshToken)
        );
    }

    // Deletes token — called on logout or token rotation
    public void delete(String refreshToken) {
        redis.delete(REFRESH_TOKEN_PREFIX + refreshToken);

        log.debug("Deleted refresh token from Redis");
    }
}