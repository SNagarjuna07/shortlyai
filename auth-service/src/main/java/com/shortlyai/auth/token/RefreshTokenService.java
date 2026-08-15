package com.shortlyai.auth.token;

import com.shortlyai.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redis;

    private final JwtUtil jwtUtil;

    private final RefreshTokenRepository refreshTokenRepository;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    // Store token in Redis (primary, fast) AND Postgres (backup + cleanup source).
    @Transactional
    public void store(String refreshToken, String userId) {

        Instant expiry = jwtUtil.extractExpiry(refreshToken);

        long ttlSeconds = Duration.between(Instant.now(), expiry).getSeconds();

        if (ttlSeconds <= 0) {

            log.warn("Refresh token already expired, not storing");

            return;
        }

        String tokenHash = sha256Hex(refreshToken);

        // Postgres first - backup + cleanup job source of truth.
        // Hash stored, never the raw token - same pattern as MCP API keys.
        RefreshToken entity = RefreshToken.builder()
                .userId(UUID.fromString(userId))
                .tokenHash(tokenHash)
                .expiresAt(expiry)
                .build();

        refreshTokenRepository.save(entity);

        // Redis write deferred to afterCommit() - same reasoning as
        // ApiKeyService.generate(): if the Postgres save above rolls back,
        // we don't want a Redis-only token with no backing row.
        TransactionSynchronizationManager.registerSynchronization(

                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        redis.opsForValue().set(
                                REFRESH_TOKEN_PREFIX + tokenHash,
                                userId,
                                Duration.ofSeconds(ttlSeconds)
                        );
                    }
                }
        );

        log.debug("Stored refresh token (Redis + Postgres) for userId: {}", userId);
    }

    // Redis is primary - exists() only checks Redis (fast path)
    public boolean exists(String refreshToken) {

        String tokenHash = sha256Hex(refreshToken);

        Boolean existsInRedis = redis.hasKey(REFRESH_TOKEN_PREFIX + tokenHash);

        if (Boolean.TRUE.equals(existsInRedis)) {

            return true;
        }

        return refreshTokenRepository
                .existsByTokenHashAndExpiresAtAfter(
                        sha256Hex(refreshToken),
                        Instant.now()
                );
    }

    // Delete from both stores - Redis invalidates immediately, Postgres cleans up audit trail
    @Transactional
    public void delete(String refreshToken) {

        String tokenHash = sha256Hex(refreshToken);

        // Postgres delete by hash - raw token never stored or sent to DB
        refreshTokenRepository.deleteByTokenHash(tokenHash);

        TransactionSynchronizationManager.registerSynchronization(

                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        redis.delete(REFRESH_TOKEN_PREFIX + tokenHash);
                    }
                }
        );

        log.debug("Deleted refresh token from Redis + Postgres");
    }

    // SHA-256 hex alg
    private String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            // SHA-256 is mandatory in every JVM - never throws
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}