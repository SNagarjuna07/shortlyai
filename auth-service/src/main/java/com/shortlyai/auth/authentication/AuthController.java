package com.shortlyai.auth.authentication;

import com.shortlyai.auth.dto.*;
import com.shortlyai.auth.email.VerificationService;
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
public class AuthController {

    private final AuthService authService;

    private final VerificationService verificationService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity.ok(authService.login(loginRequest, httpRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(registerRequest, httpRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest,
            HttpServletRequest httpRequest
    ) {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(authService.refresh(refreshTokenRequest, httpRequest));
    }

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

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal String userId
    ) {

        return ResponseEntity.ok(authService.getMe(UUID.fromString(userId)));
    }

    @GetMapping("/verify")
    public ResponseEntity<Void> accountVerification(@RequestParam String token) {

        verificationService.verifyAccount(token);

        return ResponseEntity.ok().build();
    }
}
