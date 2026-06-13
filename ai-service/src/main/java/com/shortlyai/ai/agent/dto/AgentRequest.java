package com.shortlyai.ai.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
        @NotBlank(message = "message must not be blank") String message
) {}