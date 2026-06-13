package com.shortlyai.ai.safety.dto;

import jakarta.validation.constraints.NotBlank;

public record SafetyCheckRequest(
        @NotBlank(message = "url must not be blank") String url
) {}