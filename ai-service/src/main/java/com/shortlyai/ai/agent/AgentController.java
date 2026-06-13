package com.shortlyai.ai.agent;

import com.shortlyai.ai.agent.dto.AgentRequest;
import com.shortlyai.ai.agent.dto.AgentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/ai/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public AgentResponse chat(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AgentRequest request
    ) {

        return agentService.chat(userId, request.message());
    }
}