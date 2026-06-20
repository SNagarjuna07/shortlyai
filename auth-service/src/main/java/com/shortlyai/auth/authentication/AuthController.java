package com.shortlyai.auth.authentication;

import com.shortlyai.auth.dto.*;
import com.shortlyai.auth.email.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication Controller")
public class AuthController {

    private final AuthService authService;

    private final VerificationService verificationService;

    @Operation(
            summary = "Login",
            description = "Allows users to login"
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity.ok(authService.login(loginRequest, httpRequest));
    }

    @Operation(
            summary = "Register",
            description = "Allows users to register for ShortlyAI"
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(registerRequest, httpRequest));
    }

    @Operation(
            summary = "Refresh tokens",
            description = "Generates a refresh JWT token"
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(authService.refresh(refreshTokenRequest, httpRequest));
    }

    @Operation(
            summary = "Logout",
            description = "Allows users to logout"
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest httpRequest
    ) {

        authService.logout(refreshTokenRequest, httpRequest);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @Operation(
            summary = "Current user",
            description = "Extracts current user's details"
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal String userId
    ) {

        return ResponseEntity.ok(authService.getMe(UUID.fromString(userId)));
    }

    @Operation(
            summary = "Account verification",
            description = "Allows users to verify their email"
    )
    @GetMapping("/verify")
    public ResponseEntity<Void> accountVerification(@RequestParam String token) {

        verificationService.verifyAccount(token);

        return ResponseEntity.ok().build();
    }
}
