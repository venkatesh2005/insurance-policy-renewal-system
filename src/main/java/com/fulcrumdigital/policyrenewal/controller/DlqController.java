package com.fulcrumdigital.policyrenewal.controller;

import com.fulcrumdigital.policyrenewal.entity.ReminderDlq;
import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import com.fulcrumdigital.policyrenewal.exception.DlqEntryNotFoundException;
import com.fulcrumdigital.policyrenewal.repository.ReminderDlqRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final ReminderDlqRepository dlqRepository;
    private final ReminderOutboxRepository outboxRepository;
    private final Clock clock;

    @GetMapping
    public List<ReminderDlq> listDlq() {
        return dlqRepository.findAll();
    }

    @PostMapping("/{id}/requeue")
    @Transactional
    public ReminderOutbox requeue(@PathVariable Long id) {
        ReminderDlq dlq = dlqRepository.findById(id)
                .orElseThrow(() -> new DlqEntryNotFoundException(id));

        ReminderOutbox freshEvent = ReminderOutbox.builder()
                .policyNumber(dlq.getPolicyNumber())
                .tier(dlq.getTier())
                .channel(dlq.getChannel())
                .payload(dlq.getPayload())
                .status("PENDING")
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now(clock))
                .reminderDate(LocalDate.now(clock))
                .build();

        ReminderOutbox saved = outboxRepository.save(freshEvent);
        dlqRepository.deleteById(id);
        return saved;
    }
}