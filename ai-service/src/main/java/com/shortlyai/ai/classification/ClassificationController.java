package com.shortlyai.ai.classification;

import com.shortlyai.ai.classification.dto.ClassificationRequest;
import com.shortlyai.ai.classification.dto.ClassificationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/ai/classify")
@RequiredArgsConstructor
public class ClassificationController {

    private final ClassificationService classificationService;

    @PostMapping
    public ClassificationResponse classify(
            @Valid @RequestBody ClassificationRequest request
    ) {

        return classificationService.classify(request);
    }
}