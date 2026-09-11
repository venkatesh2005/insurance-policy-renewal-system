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

// Requirement: "one test proving the retry-to-DLQ path."
//
// Pure Mockito unit test: gateway is mocked to always return GATEWAY_TIMEOUT,
// and maxAttempts is set to 1 via reflection (no Spring context needed) so
// the very first attempt is also the final attempt — making the DLQ path
// deterministic instead of depending on the random gateway simulator.
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
        // Fixed clock so nextRetryAt calculations are deterministic
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-11T00:00:00Z"),
                ZoneId.of("UTC"));

        // Construct directly — @Value fields won't be populated outside
        // a Spring context, so maxAttempts is injected below via reflection
        notificationService = new NotificationService(
                outboxRepository,
                notificationLogRepository,
                dlqRepository,
                policyRepository,
                gateway,
                backoffCalculator,
                fixedClock);

        // Set maxAttempts = 1 so the first timeout is also the last,
        // triggering the DLQ path on a single attempt
        ReflectionTestUtils.setField(notificationService, "maxAttempts", 1);
    }

    // ---------------------------------------------------------------
    // Test: first (and only allowed) attempt times out → goes to DLQ
    // ---------------------------------------------------------------
    @Test
    void processEvent_whenGatewayTimesOutOnFinalAllowedAttempt_movesEventToDlq() {
        ReminderOutbox event = ReminderOutbox.builder()
                .id(99L)
                .policyNumber("POL-DLQ-TEST")
                .tier("30_DAY")
                .channel("EMAIL")
                .payload("Your policy POL-DLQ-TEST needs renewal (30_DAY)")
                .status("PENDING")
                .retryCount(0) // attemptNumber = retryCount + 1 = 1 = maxAttempts → DLQ
                .build();

        when(gateway.send(event)).thenReturn(NotificationOutcome.GATEWAY_TIMEOUT);

        notificationService.processEvent(event);

        // Outbox row must be marked FAILED
        verify(outboxRepository).save(argThat(saved ->
                "FAILED".equals(saved.getStatus())));

        // A DLQ row must be created with correct fields
        verify(dlqRepository).save(argThat(dlq ->
                dlq.getOutboxId().equals(99L)
                        && "POL-DLQ-TEST".equals(dlq.getPolicyNumber())
                        && "30_DAY".equals(dlq.getTier())
                        && dlq.getRetryCount() == 1));

        // One notification log entry with GATEWAY_TIMEOUT outcome
        verify(notificationLogRepository).save(argThat(log ->
                log.getOutboxId().equals(99L)
                        && NotificationOutcome.GATEWAY_TIMEOUT.equals(log.getOutcome())
                        && log.getAttemptNumber() == 1));
    }

    // ---------------------------------------------------------------
    // Test: CANCELLED event is skipped before gateway is called
    // ---------------------------------------------------------------
    @Test
    void processEvent_whenEventIsNotPending_skipsWithoutCallingGateway() {
        ReminderOutbox event = ReminderOutbox.builder()
                .id(100L)
                .policyNumber("POL-CANCELLED")
                .tier("7_DAY")
                .channel("EMAIL")
                .payload("test")
                .status("CANCELLED")
                .retryCount(0)
                .build();

        notificationService.processEvent(event);

        // Gateway must never be called for a non-PENDING event
        org.mockito.Mockito.verifyNoInteractions(gateway);
        org.mockito.Mockito.verifyNoInteractions(outboxRepository);
        org.mockito.Mockito.verifyNoInteractions(notificationLogRepository);
        org.mockito.Mockito.verifyNoInteractions(dlqRepository);
    }
}