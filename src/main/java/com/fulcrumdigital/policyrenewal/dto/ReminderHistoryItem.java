package com.fulcrumdigital.policyrenewal.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReminderHistoryItem {
    private String tier;
    private String channel;
    private int attemptNumber;
    private String outcome;
    private String errorMessage;
    private LocalDateTime attemptedAt;
}