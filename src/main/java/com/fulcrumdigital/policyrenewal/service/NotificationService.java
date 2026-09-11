package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.NotificationLog;
import com.fulcrumdigital.policyrenewal.entity.ReminderDlq;
import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import com.fulcrumdigital.policyrenewal.entity.enums.NotificationOutcome;
import com.fulcrumdigital.policyrenewal.notification.BackoffCalculator;
import com.fulcrumdigital.policyrenewal.notification.NotificationGatewaySimulator;
import com.fulcrumdigital.policyrenewal.repository.NotificationLogRepository;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderDlqRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ReminderOutboxRepository outboxRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ReminderDlqRepository dlqRepository;
    private final PolicyRepository policyRepository;
    private final NotificationGatewaySimulator gateway;
    private final BackoffCalculator backoffCalculator;
    private final Clock clock;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    public void processEvent(ReminderOutbox event) {
        if (!"PENDING".equals(event.getStatus())) {
            log.info("Skipping outbox {} — status is now {}", event.getId(), event.getStatus());
            return;
        }

        int attemptNumber = event.getRetryCount() + 1;
        String outcome = gateway.send(event);

        switch (outcome) {
            case NotificationOutcome.SUCCESS        -> handleSuccess(event, attemptNumber);
            case NotificationOutcome.INVALID_CONTACT -> handleInvalidContact(event, attemptNumber);
            default                                 -> handleTimeout(event, attemptNumber);
        }
    }

    private void handleSuccess(ReminderOutbox event, int attemptNumber) {
        event.setStatus("SENT");
        outboxRepository.save(event);
        logAttempt(event, attemptNumber, NotificationOutcome.SUCCESS, null);
        log.info("Reminder {} sent (policy {}, tier {})",
                event.getId(), event.getPolicyNumber(), event.getTier());
    }

    private void handleInvalidContact(ReminderOutbox event, int attemptNumber) {
        event.setStatus("FAILED");
        outboxRepository.save(event);
        logAttempt(event, attemptNumber, NotificationOutcome.INVALID_CONTACT, "Contact rejected by gateway");

        // Wrap in try/catch — multiple workers may try to update the same
        // policy's needsManualFollowUp flag concurrently. If another worker
        // already set it, the version check fails. Since the flag is already
        // true in that case, silently ignore the conflict.
        try {
            policyRepository.findByPolicyNumber(event.getPolicyNumber()).ifPresent(policy -> {
                policy.setNeedsManualFollowUp(true);
                policyRepository.save(policy);
            });
        } catch (Exception ex) {
            log.warn("Could not flag policy {} for manual follow-up (concurrent update) — flag may already be set",
                    event.getPolicyNumber());
        }

        log.warn("Reminder {} — invalid contact for policy {}. Flagged for manual follow-up.",
                event.getId(), event.getPolicyNumber());
    }

    private void handleTimeout(ReminderOutbox event, int attemptNumber) {
        logAttempt(event, attemptNumber, NotificationOutcome.GATEWAY_TIMEOUT, "Gateway timeout");

        if (attemptNumber >= maxAttempts) {
            moveToDlq(event, "Gateway timeout after " + attemptNumber + " attempts");
        } else {
            event.setRetryCount(attemptNumber);
            event.setNextRetryAt(backoffCalculator.nextRetryAt(attemptNumber, clock));
            outboxRepository.save(event);
            log.info("Reminder {} timed out (attempt {}), next retry at {}",
                    event.getId(), attemptNumber, event.getNextRetryAt());
        }
    }

    private void moveToDlq(ReminderOutbox event, String lastError) {
        event.setStatus("FAILED");
        outboxRepository.save(event);

        ReminderDlq dlq = ReminderDlq.builder()
                .outboxId(event.getId())
                .policyNumber(event.getPolicyNumber())
                .tier(event.getTier())
                .channel(event.getChannel())
                .payload(event.getPayload())
                .retryCount(event.getRetryCount() + 1)
                .lastError(lastError)
                .build();
        dlqRepository.save(dlq);

        log.warn("Reminder {} moved to DLQ (policy {}, tier {}): {}",
                event.getId(), event.getPolicyNumber(), event.getTier(), lastError);
    }

    private void logAttempt(ReminderOutbox event, int attemptNumber, String outcome, String errorMessage) {
        NotificationLog entry = NotificationLog.builder()
                .outboxId(event.getId())
                .policyNumber(event.getPolicyNumber())
                .tier(event.getTier())
                .channel(event.getChannel())
                .attemptNumber(attemptNumber)
                .outcome(outcome)
                .errorMessage(errorMessage)
                .build();
        notificationLogRepository.save(entry);
    }
}
