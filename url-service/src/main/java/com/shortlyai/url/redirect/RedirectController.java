package com.shortlyai.url.redirect;

import com.shortlyai.url.shortening.ShorteningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Public controller — no authentication required
// Anyone with the short URL can follow the redirect
@RestController
@RequestMapping("/api/v1/r")
@RequiredArgsConstructor
@Slf4j
public class RedirectController {

    // Interface injection — never the concrete impl
    private final ShorteningService shorteningService;

    // GET /api/v1/r/{slug} — resolve slug to original URL and redirect
    // No @AuthenticationPrincipal — this endpoint is intentionally public
    @GetMapping("/{slug}")
    public ResponseEntity<Void> redirect(
            @PathVariable String slug
    ) {

        log.info("Redirect request for slug: {}", slug);

        // Redis first, Postgres fallback — handled inside resolve()
        String longUrl = shorteningService.resolve(slug);

        // Set Location header — browser follows this to the original URL
        HttpHeaders headers = new HttpHeaders();

        headers.add(HttpHeaders.LOCATION, longUrl);

        // 302 FOUND not 301 — temporary redirect
        // 301 is cached permanently by browsers — kills click analytics
        // 302 forces every click to hit our server — we track every redirect
        return ResponseEntity.status(HttpStatus.FOUND)
                .headers(headers).build();
    }


}
