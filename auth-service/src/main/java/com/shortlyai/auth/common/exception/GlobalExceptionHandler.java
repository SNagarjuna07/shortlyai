package com.shortlyai.auth.common.exception;

import com.shortlyai.auth.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

// @RestControllerAdvice — intercepts exceptions thrown by any controller
// Single place for all error handling — no try/catch in controllers or services
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handles @Valid failures — @NotBlank, @Email, @Size violations
    // Spring throws this automatically when validation fails on @RequestBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        // getBindingResult() — contains all field errors from @Valid
        // getFieldErrors() — list of individual field violations
        // stream first error only — return one message at a time, not a dump
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        log.error("Validation error: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    // 401 — wrong email or password
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {

        log.error("Invalid credentials: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // 409 Conflict — email already registered
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {

        log.error("Email already exists: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotVerified(
            AccountNotVerifiedException ex,
            HttpServletRequest request
    ) {

        log.error("User not verified: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // 401 - handles when user passes an invalid JWT
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex,
            HttpServletRequest request
    ) {

        log.error("Invalid JWT: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // 404 - handles when user is not present
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(
            UserNotFoundException ex,
            HttpServletRequest request
    ) {

        log.error("User not found: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // @PreAuthorize failure
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    // MCP-API-key not found
    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleApiKeyNotFound(
            ApiKeyNotFoundException ex,
            HttpServletRequest request
    ) {

        log.error("MCP API key not found: {}", ex.getMessage());

        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // 500 — catch-all for anything unexpected
    // Never expose internal details — log it, return generic message
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // DRY — single builder used by all handlers
    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(),        // numeric code — 400, 401, 409, 500
                message,               // human readable
                request.getRequestURI(), // which endpoint failed
                Instant.now()          // when it happened
        ));
    }
}