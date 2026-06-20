package com.shortlyai.ai.agent;

import com.shortlyai.ai.agent.dto.AgentRequest;
import com.shortlyai.ai.agent.dto.AgentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/ai/agent")
@RequiredArgsConstructor
@Tag(name = "AI Agent Controller")
public class AgentController {

    private final AgentService agentService;

    @Operation(
            summary = "Chat with AI agent" ,
            description = "Users talk to it in plain English about their URLs and get their answers"
    )
    @PostMapping
    public AgentResponse chat(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AgentRequest request
    ) {

        return agentService.chat(userId, request.message());
    }
}