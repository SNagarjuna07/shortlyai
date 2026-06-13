package com.shortlyai.ai.slug;

import com.shortlyai.ai.slug.dto.SlugRequest;
import com.shortlyai.ai.slug.dto.SlugResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/ai/slug")
@RequiredArgsConstructor
public class SlugController {

    private final SlugService slugService;

    @PostMapping("/suggest")
    public SlugResponse suggest(
            @Valid @RequestBody SlugRequest request
    ) {

        return slugService.suggest(request);
    }
}