package com.fulcrumdigital.policyrenewal.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class BackoffCalculator {

    @Value("${notification.retry.base-delay-minutes:1}")
    private int baseDelayMinutes;

    public LocalDateTime nextRetryAt(int attemptNumber, Clock clock) {
        long delayMinutes = (long) Math.pow(4, attemptNumber - 1) * baseDelayMinutes;
        return LocalDateTime.now(clock).plusMinutes(delayMinutes);
    }
}