package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class ReminderOutboxWriter {

    private final ReminderOutboxRepository outboxRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Policy policy, String tier) {
        ReminderOutbox outboxRow = ReminderOutbox.builder()
                .policyNumber(policy.getPolicyNumber())
                .tier(tier)
                .channel(policy.getEmail() != null ? "EMAIL" : "SMS")
                .payload("Your policy " + policy.getPolicyNumber() + " needs renewal (" + tier + ")")
                .status("PENDING")
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now(clock))
                .reminderDate(LocalDate.now(clock))
                .build();
        outboxRepository.saveAndFlush(outboxRow);
    }
}
