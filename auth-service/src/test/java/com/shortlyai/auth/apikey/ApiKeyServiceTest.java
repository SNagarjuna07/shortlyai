package com.shortlyai.auth.apikey;

import com.shortlyai.auth.apikey.dto.ApiKeyGenerateRequest;
import com.shortlyai.auth.apikey.dto.ApiKeyMetadataResponse;
import com.shortlyai.auth.apikey.dto.ApiKeyResponse;
import com.shortlyai.auth.common.exception.ApiKeyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyServiceTest {

    @Mock
    ApiKeyRepository apiKeyRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @InjectMocks
    ApiKeyService apiKeyService;

    private static final UUID USER_ID = UUID.randomUUID();

    private static final String KEY_PREFIX = "mcp:key:";

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void generate_persistsEntityAndCachesInRedis() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("My Cursor Key");

        ApiKey savedKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .keyPrefix("ab12cd34")
                .keyHash("fakehash")
                .name("My Cursor Key")
                .createdAt(Instant.now())
                .build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedKey);

        ApiKeyResponse response = apiKeyService.generate(USER_ID, request);

        // rawKey must be returned exactly once and start with "sk_"
        assertThat(response.rawKey()).startsWith("sk_");

        assertThat(response.name()).isEqualTo("My Cursor Key");

        assertThat(response.id()).isEqualTo(savedKey.getId());

        // DB save called
        verify(apiKeyRepository).save(any(ApiKey.class));

        // Redis set called with hash-based key (not raw key)
        ArgumentCaptor<String> redisKeyCaptor = ArgumentCaptor.forClass(String.class);

        verify(valueOps).set(redisKeyCaptor.capture(), eq(USER_ID.toString()));

        assertThat(redisKeyCaptor.getValue()).startsWith(KEY_PREFIX);

        // key must not contain the raw key itself — only the hash
        assertThat(redisKeyCaptor.getValue()).doesNotContain("sk_");
    }

    @Test
    void generate_rawKeyIsDifferentEachCall() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("Key");

        ApiKey key1 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("aaaa1111").keyHash("hash1").name("Key").createdAt(Instant.now()).build();

        ApiKey key2 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("bbbb2222").keyHash("hash2").name("Key").createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any())).thenReturn(key1).thenReturn(key2);

        ApiKeyResponse r1 = apiKeyService.generate(USER_ID, request);

        ApiKeyResponse r2 = apiKeyService.generate(USER_ID, request);

        // SecureRandom ensures uniqueness
        assertThat(r1.rawKey()).isNotEqualTo(r2.rawKey());
    }

    @Test
    void generate_storedEntityContainsHashNotRawKey() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("Test");

        ApiKey savedKey = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("ab12cd34").keyHash("somehash").name("Test").createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedKey);

        ApiKeyResponse response = apiKeyService.generate(USER_ID, request);

        ArgumentCaptor<ApiKey> entityCaptor = ArgumentCaptor.forClass(ApiKey.class);

        verify(apiKeyRepository).save(entityCaptor.capture());

        ApiKey entity = entityCaptor.getValue();

        // hash must be 64-char hex (SHA-256)
        assertThat(entity.getKeyHash()).hasSize(64);

        assertThat(entity.getKeyHash()).matches("[a-f0-9]+");

        // raw key must NOT be stored
        assertThat(entity.getKeyHash()).doesNotContain("sk_");
    }

    @Test
    void list_returnsMetadataForUser() {

        ApiKey k1 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("aa11bb22").keyHash("h1").name("Key 1").createdAt(Instant.now()).build();

        ApiKey k2 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("cc33dd44").keyHash("h2").name("Key 2").createdAt(Instant.now()).build();

        when(apiKeyRepository.findAllByUserId(USER_ID)).thenReturn(List.of(k1, k2));

        List<ApiKeyMetadataResponse> result = apiKeyService.list(USER_ID);

        assertThat(result).hasSize(2);

        assertThat(result).extracting(ApiKeyMetadataResponse::name)
                .containsExactlyInAnyOrder("Key 1", "Key 2");
        // rawKey must NOT appear in metadata response (no rawKey field in record)
    }

    @Test
    void list_noKeys_returnsEmptyList() {

        when(apiKeyRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        assertThat(apiKeyService.list(USER_ID)).isEmpty();
    }

    @Test
    void revoke_existingKey_deletesFromRedisAndDb() {

        UUID keyId = UUID.randomUUID();

        ApiKey key = ApiKey.builder().id(keyId).userId(USER_ID)
                .keyPrefix("ab12cd34").keyHash("thehash").name("Key").createdAt(Instant.now()).build();

        when(apiKeyRepository.findByIdAndUserId(keyId, USER_ID)).thenReturn(Optional.of(key));

        apiKeyService.revoke(USER_ID, keyId);

        // Redis deleted first (invalidates key immediately)
        verify(stringRedisTemplate).delete(KEY_PREFIX + "thehash");

        // DB deleted after
        verify(apiKeyRepository).delete(key);
    }

    @Test
    void revoke_keyNotOwnedByUser_throwsApiKeyNotFound() {

        UUID keyId = UUID.randomUUID();

        when(apiKeyRepository.findByIdAndUserId(keyId, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(USER_ID, keyId))
                .isInstanceOf(ApiKeyNotFoundException.class);

        // Neither Redis nor DB should be touched
        verify(stringRedisTemplate, never()).delete(anyString());

        verify(apiKeyRepository, never()).delete(any());
    }
}