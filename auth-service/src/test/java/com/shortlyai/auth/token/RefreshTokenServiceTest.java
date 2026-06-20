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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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

    private static final String TOKEN   = "sample.refresh.token";

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void store_validToken_setsRedisKeyWithTtl() {

        Instant future = Instant.now().plusSeconds(3600);

        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(future);

        refreshTokenService.store(TOKEN, USER_ID);

        // key must be namespaced "refresh:<token>", value = userId, TTL > 0
        verify(valueOps).set(eq("refresh:" + TOKEN), eq(USER_ID), any(Duration.class));
    }

    @Test
    void store_alreadyExpiredToken_skipsRedis() {

        // expiry in the past → TTL would be negative → should not store
        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(Instant.now().minusSeconds(10));

        refreshTokenService.store(TOKEN, USER_ID);

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void store_ttlMatchesTokenExpiry() {

        Instant expiry = Instant.now().plusSeconds(3600);

        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(expiry);

        refreshTokenService.store(TOKEN, USER_ID);

        // capture Duration arg and assert it's <= 3600s (some millis may pass)
        var captor = org.mockito.ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(any(), any(), captor.capture());

        assertThat(captor.getValue().getSeconds()).isLessThanOrEqualTo(3600L);

        assertThat(captor.getValue().getSeconds()).isGreaterThan(0L);
    }

    @Test
    void exists_tokenPresentInRedis_returnsTrue() {

        when(redis.hasKey("refresh:" + TOKEN)).thenReturn(true);

        assertThat(refreshTokenService.exists(TOKEN)).isTrue();
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

    @Test
    void delete_callsRedisDeleteWithNamespacedKey() {

        refreshTokenService.delete(TOKEN);

        verify(redis).delete("refresh:" + TOKEN);
    }
}