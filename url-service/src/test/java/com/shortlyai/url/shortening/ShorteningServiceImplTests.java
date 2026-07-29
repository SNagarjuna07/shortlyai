package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;
import com.shortlyai.url.common.exception.DuplicateSlugException;
import com.shortlyai.url.common.exception.UrlNotFoundException;
import com.shortlyai.url.dlq.FailedEventService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShorteningServiceImplTests {

    @Mock
    UrlRepository urlRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    FailedEventService failedEventService;

    @Mock
    HttpServletRequest httpServletRequest;

    ShorteningServiceImpl shorteningService;

    private UUID userId;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();

        shorteningService = new ShorteningServiceImpl(
                urlRepository,
                stringRedisTemplate,
                kafkaTemplate,
                "http://short.ly",   // baseDomain
                "/api/v1",           // apiPrefix
                30L,                 // defaultExpiryDays
                3600L,               // cacheTtlSeconds
                "url.created",
                "url.clicks",
                "url.deleted",
                failedEventService,
                Runnable::run       // same-thread executor — click tracking
                // runs synchronously in tests, so
                // verify() right after resolve() sees it
        );

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // Kafka send() returns a CompletableFuture in Boot 4 — whenComplete must not NPE
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("junit-agent");
        when(httpServletRequest.getHeader("Referer")).thenReturn(null);
    }

    private Url buildUrl(Long id, String slug, boolean custom) {

        return Url.builder()
                .id(id)
                .slug(slug)
                .originalUrl("https://example.com/page")
                .userId(userId)
                .isCustom(custom)
                .clickCount(0L)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void shorten_customSlugNotTaken_savesAndReturnsResponse() {

        ShortenRequest request = new ShortenRequest("https://example.com", "my-slug", null);

        when(urlRepository.existsBySlug("my-slug")).thenReturn(false);

        Url saved = buildUrl(1L, "my-slug", true);
        when(urlRepository.save(any(Url.class))).thenReturn(saved);

        ShortenResponse response = shorteningService.shorten(request, userId);

        assertThat(response.slug()).isEqualTo("my-slug");
        assertThat(response.shortUrl()).isEqualTo("http://short.ly/api/v1/r/my-slug");
        assertThat(response.userId()).isEqualTo(userId);

        // custom slug path never regenerates the slug via Base62 retry loop
        verify(urlRepository, times(1)).save(any(Url.class));
        verify(valueOperations).set(eq("url:my-slug"), anyString(), any(Duration.class));
    }

    @Test
    void shorten_customSlugAlreadyTaken_throwsDuplicateSlug() {

        ShortenRequest request = new ShortenRequest("https://example.com", "taken", null);

        when(urlRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> shorteningService.shorten(request, userId))
                .isInstanceOf(DuplicateSlugException.class);

        verify(urlRepository, never()).save(any());
    }

    @Test
    void shorten_noCustomSlug_generatesRandomSlugAndRetriesOnCollision() {

        ShortenRequest request = new ShortenRequest("https://example.com", null, null);

        // first save creates row with a tmp slug (no collision check needed for tmp prefix)
        Url initialSave = buildUrl(5L, "tmp-placeholder", false);
        when(urlRepository.save(any(Url.class))).thenReturn(initialSave).thenAnswer(inv -> inv.getArgument(0));

        // simulate one collision, then success
        when(urlRepository.existsBySlug(anyString())).thenReturn(true, false);

        ShortenResponse response = shorteningService.shorten(request, userId);

        assertThat(response).isNotNull();
        // save called twice: once to get an ID, once after slug finalized
        verify(urlRepository, times(2)).save(any(Url.class));
        verify(urlRepository, times(2)).existsBySlug(anyString());
    }

    @Test
    void shorten_exceedsMaxSlugRetries_throwsIllegalState() {

        ShortenRequest request = new ShortenRequest("https://example.com", null, null);

        Url initialSave = buildUrl(9L, "tmp-placeholder", false);
        when(urlRepository.save(any(Url.class))).thenReturn(initialSave);

        // every generated slug collides — forces the retry limit to trip
        when(urlRepository.existsBySlug(anyString())).thenReturn(true);

        assertThatThrownBy(() -> shorteningService.shorten(request, userId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shorten_kafkaSendFailsSynchronously_savesToDlq() {

        ShortenRequest request = new ShortenRequest("https://example.com", "custom", null);

        when(urlRepository.existsBySlug("custom")).thenReturn(false);
        when(urlRepository.save(any(Url.class))).thenReturn(buildUrl(2L, "custom", true));

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("broker unreachable"));

        // shorten() must not blow up even though the synchronous Kafka call throws
        shorteningService.shorten(request, userId);

        verify(failedEventService).save(eq("url.created"), eq("custom"), any(), anyString());
    }

    // ---------- resolve() ----------

    @Test
    void resolve_cacheHitNotExpired_returnsUrlWithoutHittingDb() {

        long urlId = 10L;
        long expiresAtMs = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        String cached = urlId + "\u0000" + userId + "\u0000" + expiresAtMs + "\u0000https://example.com/x";

        when(valueOperations.get("url:abc123")).thenReturn(cached);

        String result = shorteningService.resolve("abc123", httpServletRequest);

        assertThat(result).isEqualTo("https://example.com/x");
        verify(urlRepository, never()).findBySlugAndIsActiveTrueAndExpiresAtAfter(any(), any());
        verify(valueOperations).increment("clicks:pending:" + urlId);
    }

    @Test
    void resolve_cacheHitButExpired_evictsAndThrowsNotFound() {

        long urlId = 11L;
        long expiresAtMs = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli(); // already expired
        String cached = urlId + "\u0000" + userId + "\u0000" + expiresAtMs + "\u0000https://example.com/y";

        when(valueOperations.get("url:expired-slug")).thenReturn(cached);

        assertThatThrownBy(() -> shorteningService.resolve("expired-slug", httpServletRequest))
                .isInstanceOf(UrlNotFoundException.class);

        verify(stringRedisTemplate).delete("url:expired-slug");
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void resolve_staleThreePartCacheFormat_evictsAndFallsBackToDb() {

        // old pre-migration format — only 3 parts, missing the 4th field
        when(valueOperations.get("url:legacy-slug")).thenReturn("10" + "\u0000" + userId + "\u0000123456");

        Url dbUrl = buildUrl(10L, "legacy-slug", false);
        when(urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(eq("legacy-slug"), any(Instant.class)))
                .thenReturn(Optional.of(dbUrl));

        String result = shorteningService.resolve("legacy-slug", httpServletRequest);

        assertThat(result).isEqualTo(dbUrl.getOriginalUrl());
        verify(stringRedisTemplate).delete("url:legacy-slug");
        // falls through to DB and re-warms cache
        verify(valueOperations).set(eq("url:legacy-slug"), anyString(), any(Duration.class));
    }

    @Test
    void resolve_cacheMiss_fallsBackToDbAndWarmsCache() {

        when(valueOperations.get("url:db-only")).thenReturn(null);

        Url dbUrl = buildUrl(20L, "db-only", false);
        when(urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(eq("db-only"), any(Instant.class)))
                .thenReturn(Optional.of(dbUrl));

        String result = shorteningService.resolve("db-only", httpServletRequest);

        assertThat(result).isEqualTo(dbUrl.getOriginalUrl());
        verify(valueOperations).increment("clicks:pending:20");
        verify(valueOperations).set(eq("url:db-only"), anyString(), any(Duration.class));
    }

    @Test
    void resolve_notFoundAnywhere_throwsUrlNotFound() {

        when(valueOperations.get("url:ghost")).thenReturn(null);
        when(urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(eq("ghost"), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> shorteningService.resolve("ghost", httpServletRequest))
                .isInstanceOf(UrlNotFoundException.class);
    }

    // ---------- delete() / deleteUrl() ----------

    @Test
    void delete_ownedAndActive_softDeletesAndEvictsCache() {

        Url url = buildUrl(30L, "to-delete", false);

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(30L, userId)).thenReturn(Optional.of(url));
        when(urlRepository.softDeleteByIdAndUserId(eq(30L), eq(userId), any(Instant.class))).thenReturn(1);

        shorteningService.delete(30L, userId);

        verify(urlRepository).softDeleteByIdAndUserId(eq(30L), eq(userId), any(Instant.class));
        verify(stringRedisTemplate).delete("url:to-delete");
    }

    @Test
    void delete_notOwned_throwsUrlNotFound() {

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(30L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shorteningService.delete(30L, userId))
                .isInstanceOf(UrlNotFoundException.class);

        verify(urlRepository, never()).softDeleteByIdAndUserId(anyLong(), any(), any());
    }

    @Test
    void delete_raceConditionZeroRowsAffected_throwsUrlNotFound() {

        // findByIdAndUserIdAndIsActiveTrue succeeded, but another request deleted it
        // in between the read and the write — rowsDeleted comes back 0
        Url url = buildUrl(31L, "raced", false);

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(31L, userId)).thenReturn(Optional.of(url));
        when(urlRepository.softDeleteByIdAndUserId(eq(31L), eq(userId), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> shorteningService.delete(31L, userId))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void deleteUrl_bySlug_softDeletesAndEvictsCache() {

        Url url = buildUrl(40L, "by-slug", false);

        when(urlRepository.findBySlugAndUserIdAndIsActiveTrue("by-slug", userId)).thenReturn(Optional.of(url));
        when(urlRepository.softDeleteBySlugAndUserId(eq("by-slug"), eq(userId), any(Instant.class))).thenReturn(1);

        shorteningService.deleteUrl("by-slug", userId);

        verify(stringRedisTemplate).delete("url:by-slug");
    }

    @Test
    void deleteUrl_notFound_throwsUrlNotFound() {

        when(urlRepository.findBySlugAndUserIdAndIsActiveTrue("missing", userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shorteningService.deleteUrl("missing", userId))
                .isInstanceOf(UrlNotFoundException.class);
    }

    // ---------- getUrl() / getUrlBySlug() / getUserUrls() ----------

    @Test
    void getUrl_ownedAndActive_returnsResponse() {

        Url url = buildUrl(50L, "mine", false);
        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(50L, userId)).thenReturn(Optional.of(url));

        ShortenResponse response = shorteningService.getUrl(50L, userId);

        assertThat(response.id()).isEqualTo(50L);
    }

    @Test
    void getUrl_notOwned_throwsUrlNotFound() {

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(50L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shorteningService.getUrl(50L, userId))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void getUserUrls_delegatesToRepositoryWithPageable() {

        when(urlRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var page = shorteningService.getUserUrls(userId, PageRequest.of(0, 10));

        assertThat(page).isEmpty();
        verify(urlRepository).findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(eq(userId), any());
    }
}