package com.shortlyai.ai.slug.dto;

import jakarta.validation.constraints.NotBlank;

public record SlugRequest(
        @NotBlank(message = "url must not be blank") String url,
        String context   // optional: e.g. "blog post about Spring Boot 4"
) {}