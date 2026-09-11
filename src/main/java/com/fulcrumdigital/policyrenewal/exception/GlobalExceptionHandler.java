package com.fulcrumdigital.policyrenewal.exception;

import com.fulcrumdigital.policyrenewal.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PolicyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PolicyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidPolicyException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidPolicyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(DlqEntryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDlqNotFound(DlqEntryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("NOT_FOUND", ex.getMessage()));
    }

    // Safety net: anything not caught above (e.g. an unexpected runtime
    // error) still comes back as the app's standard ErrorResponse shape
    // instead of Spring Boot's default whitelabel HTML error page.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("INTERNAL_ERROR", "Something went wrong. Please try again."));
    }

    // Triggered automatically when @Valid finds a field annotation failure
    // (e.g. @NotBlank, @Positive) on a request DTO.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("VALIDATION_FAILED", message));
    }

    private ErrorResponse buildError(String error, String message) {
        return ErrorResponse.builder()
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}