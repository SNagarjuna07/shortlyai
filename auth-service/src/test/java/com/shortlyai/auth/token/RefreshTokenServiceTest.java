package com.shortlyai.auth.token;

import com.shortlyai.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    JwtUtil jwtUtil;

    @InjectMocks
    RefreshTokenService refreshTokenService;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    private static final String TOKEN   = "sample.refresh.token";

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void store_alreadyExpiredToken_skipsRedis() {

        // expiry in the past → TTL would be negative → should not store
        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(Instant.now().minusSeconds(10));

        refreshTokenService.store(TOKEN, USER_ID);

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void exists_tokenPresentInRedis_returnsTrue() {

        String expectedKey = "refresh:" + sha256Hex(TOKEN);

        when(redis.hasKey(expectedKey)).thenReturn(true);

        assertThat(refreshTokenService.exists(TOKEN)).isTrue();

        verify(redis).hasKey(expectedKey);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void exists_tokenAbsentFromRedis_returnsFalse() {

        when(redis.hasKey("refresh:" + TOKEN)).thenReturn(false);

        assertThat(refreshTokenService.exists(TOKEN)).isFalse();
    }

    @Test
    void exists_redisReturnsNull_returnsFalse() {

        // Boolean.TRUE.equals(null) = false
        when(redis.hasKey("refresh:" + TOKEN)).thenReturn(null);

        assertThat(refreshTokenService.exists(TOKEN)).isFalse();
    }

    private String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(e);
        }
    }
}