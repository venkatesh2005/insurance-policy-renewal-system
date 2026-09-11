package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import com.fulcrumdigital.policyrenewal.entity.enums.NotificationOutcome;
import com.fulcrumdigital.policyrenewal.notification.BackoffCalculator;
import com.fulcrumdigital.policyrenewal.notification.NotificationGatewaySimulator;
import com.fulcrumdigital.policyrenewal.repository.NotificationLogRepository;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderDlqRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private ReminderOutboxRepository outboxRepository;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private ReminderDlqRepository dlqRepository;
    @Mock private PolicyRepository policyRepository;
    @Mock private NotificationGatewaySimulator gateway;
    @Mock private BackoffCalculator backoffCalculator;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneId.of("UTC"));
        notificationService = new NotificationService(
                outboxRepository, notificationLogRepository, dlqRepository,
                policyRepository, gateway, backoffCalculator, fixedClock);
        ReflectionTestUtils.setField(notificationService, "maxAttempts", 1);
    }

    @Test
    void processEvent_whenGatewayTimesOutOnFinalAttempt_movesEventToDlq() {
        ReminderOutbox event = ReminderOutbox.builder()
                .id(99L)
                .policyNumber("POL-DLQ-TEST")
                .tier("30_DAY")
                .channel("EMAIL")
                .payload("test payload")
                .status("PENDING")
                .retryCount(0)
                .build();

        when(gateway.send(event)).thenReturn(NotificationOutcome.GATEWAY_TIMEOUT);

        notificationService.processEvent(event);

        verify(outboxRepository).save(argThat(e -> "FAILED".equals(e.getStatus())));
        verify(dlqRepository).save(argThat(dlq ->
                dlq.getOutboxId().equals(99L) &&
                dlq.getPolicyNumber().equals("POL-DLQ-TEST") &&
                dlq.getTier().equals("30_DAY")));
        verify(notificationLogRepository).save(argThat(log ->
                log.getOutboxId().equals(99L) &&
                NotificationOutcome.GATEWAY_TIMEOUT.equals(log.getOutcome()) &&
                log.getAttemptNumber() == 1));
    }
}
