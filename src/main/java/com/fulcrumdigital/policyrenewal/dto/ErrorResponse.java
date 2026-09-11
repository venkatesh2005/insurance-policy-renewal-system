package com.fulcrumdigital.policyrenewal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ErrorResponse {
    private String error;
    private String message;
    private LocalDateTime timestamp;
}