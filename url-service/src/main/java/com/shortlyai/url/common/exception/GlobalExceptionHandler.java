package com.shortlyai.url.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.shortlyai.url.common.dto.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

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

    // 404 — URL not found
    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(
            UrlNotFoundException ex,
            HttpServletRequest request
    ) {

        log.error("Invalid URL requested: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // 409 Conflict — slug already taken
    @ExceptionHandler(DuplicateSlugException.class)
    public ResponseEntity<ErrorResponse> handleSlugAlreadyExists(
            DuplicateSlugException ex,
            HttpServletRequest request
    ) {

        log.error("Slug already exists: {}", ex.getMessage(), ex);

        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // Handles path/query parameter type conversion failures
    // GET /api/v1/urls/abc
    // but controller expects:
    // @PathVariable Long id
    // Spring cannot convert "abc" -> Long
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {

        log.warn(
                "Invalid parameter '{}' with value '{}'",
                ex.getName(),
                ex.getValue()
        );

        return buildResponse(HttpStatus.BAD_REQUEST,"Invalid parameter value", request);
    }

    // Handles database constraint violations
    // Example:
    // Two users attempt to create the same custom slug at the same time.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {

        log.warn("Database constraint violation: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.CONFLICT,
                "Resource already exists",
                request
        );
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
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
