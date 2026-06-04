package com.shortlyai.url.common.dto;

import jakarta.validation.constraints.*;

// User gives an URL to shorten
public record ShortenRequest(

        // @NotBlank — rejects null, empty, and whitespace-only strings
        @NotBlank(message = "URL is required")
        @Pattern(
                regexp = "^https?://.*",
                message = "URL must start with http:// or https://"
        )
        String originalUrl,

        // Optional custom slug — null means system generates via Base62
        // @Size — prevents abuse (slugs can't be longer than 20 chars)
        @Size(max = 20, message = "Custom slug cannot exceed 20 characters")
        String customSlug,

        // Optional expiry in days — null means use default (30 days)
        @Positive(message = "Expiry days must be greater than 0")
        @Max(value = 365, message = "Expiry days cannot exceed 365")
        Integer expiryDays
) {}