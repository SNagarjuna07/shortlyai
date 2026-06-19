package com.shortlyai.auth.apikey;

import com.shortlyai.auth.apikey.dto.ApiKeyGenerateRequest;
import com.shortlyai.auth.apikey.dto.ApiKeyMetadataResponse;
import com.shortlyai.auth.apikey.dto.ApiKeyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/auth/apikeys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiKeyResponse> generate(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ApiKeyGenerateRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        apiKeyService.generate(
                                UUID.fromString(userId),
                                request)
                );
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyMetadataResponse>> list(
            @AuthenticationPrincipal String userId
    ) {

        return ResponseEntity.ok(
                apiKeyService.list(
                        UUID.fromString(userId)
                )
        );
    }

    // {id} = key UUID from the list response
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id
    ) {

        apiKeyService.revoke(UUID.fromString(userId), id);

        return ResponseEntity.noContent().build();
    }
}