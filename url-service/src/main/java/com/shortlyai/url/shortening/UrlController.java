package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/urls")
@Slf4j
public class UrlController {

    private final ShorteningService shorteningService;

    @PostMapping
    public ResponseEntity<ShortenResponse> shortenURL(
            @Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Create URL request from userId={}", userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        shorteningService.shorten(
                                request, userId)
                );
    }

    @GetMapping
    public ResponseEntity<Page<ShortenResponse>> getAllUrls(
            @AuthenticationPrincipal UUID userId,
            Pageable pageable
    ) {

        log.info("Get URLs for userId={}", userId);

        return ResponseEntity.ok(shorteningService.getUserUrls(userId, pageable));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ShortenResponse> getUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Get URL id={} for userId={}", id, userId);

        return ResponseEntity.ok(shorteningService.getUrl(id, userId));

    }

    @GetMapping("/{slug}")
    public ResponseEntity<ShortenResponse> getUrlBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Get URL slug: {} for userId: {}", slug, userId);

        return ResponseEntity.ok(shorteningService.getUrlBySlug(slug, userId));
    }

    @DeleteMapping("/{id:\\\\d+}")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UUID userId
    ) {

        log.info("Delete URL id={} for userId={}", id, userId);

        shorteningService.delete(id, userId);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/delete/{slug}")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable String slug,
            @RequestHeader("X-User-Id") UUID userId
    ) {

        log.debug("Deleting slug '{}'", slug);

        shorteningService.deleteUrl(slug, userId);

        return ResponseEntity.noContent().build(); // 204
    }
}
