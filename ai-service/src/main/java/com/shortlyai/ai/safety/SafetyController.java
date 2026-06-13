package com.shortlyai.ai.safety;

import com.shortlyai.ai.safety.dto.SafetyCheckRequest;
import com.shortlyai.ai.safety.dto.SafetyCheckResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/ai/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @PostMapping("/check")
    public SafetyCheckResponse check(
            @Valid @RequestBody SafetyCheckRequest request
    ) {

        return safetyService.check(request);
    }
}