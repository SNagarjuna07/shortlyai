package com.shortlyai.url.expiry;

import com.shortlyai.url.shortening.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpiryCleanupJobTests {

    @Mock
    UrlRepository urlRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    ExpiryCleanupJob expiryCleanupJob;

    @BeforeEach
    void setUp() {
        expiryCleanupJob = new ExpiryCleanupJob(urlRepository, stringRedisTemplate);
    }

    @Test
    void cleanupExpiredUrls_noExpiredUrls_skipsDeactivationAndEviction() {

        when(urlRepository.findExpiredSlugs(any(Instant.class))).thenReturn(List.of());

        expiryCleanupJob.cleanupExpiredUrls();

        verify(urlRepository, never()).deactivateExpiredUrls(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void cleanupExpiredUrls_hasExpiredUrls_deactivatesAndEvictsEachFromCache() {

        List<String> expiredSlugs = List.of("slug1", "slug2", "slug3");

        when(urlRepository.findExpiredSlugs(any(Instant.class))).thenReturn(expiredSlugs);
        when(urlRepository.deactivateExpiredUrls(any(Instant.class))).thenReturn(3);

        expiryCleanupJob.cleanupExpiredUrls();

        verify(urlRepository).deactivateExpiredUrls(any(Instant.class));
        verify(stringRedisTemplate).delete("url:slug1");
        verify(stringRedisTemplate).delete("url:slug2");
        verify(stringRedisTemplate).delete("url:slug3");
    }

    @Test
    void cleanupExpiredUrls_evictsExactCountMatchingSlugList() {

        List<String> expiredSlugs = List.of("only-one");

        when(urlRepository.findExpiredSlugs(any(Instant.class))).thenReturn(expiredSlugs);
        when(urlRepository.deactivateExpiredUrls(any(Instant.class))).thenReturn(1);

        expiryCleanupJob.cleanupExpiredUrls();

        verify(stringRedisTemplate, times(1)).delete(anyString());
    }
}