package com.shortlyai.ai.classification.dto;

import jakarta.validation.constraints.NotBlank;

public record ClassificationRequest(
        @NotBlank(message = "url must not be blank") String url
) {}