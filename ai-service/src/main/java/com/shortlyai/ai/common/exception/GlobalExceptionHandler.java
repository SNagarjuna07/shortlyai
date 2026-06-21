package com.shortlyai.ai.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // @Valid failures on request bodies
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Validation failed");

        log.warn("Validation failed: {}", message);

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(Instant.now(), 400, "BAD_REQUEST", message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        // 404 not 500 — mcp-remote OAuth discovery needs clean 404 to fall back gracefully
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(Instant.now(), 404, "Resource not found", ex.getMessage()));
    }

    // downstream service (url-service / analytics-service) returned 4xx/5xx
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleDownstream(RestClientResponseException ex) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());

        log.error("Downstream service error: status= {}, body= {}",
                status, ex.getResponseBodyAsString()
        );

        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.name(),
                        "Downstream service error: " + ex.getMessage()));
    }

    // catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {

        log.error("Unhandled exception", ex);

        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(Instant.now(), 500, "INTERNAL_ERROR", ex.getMessage()));
    }
}