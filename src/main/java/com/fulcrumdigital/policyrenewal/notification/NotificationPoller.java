package com.fulcrumdigital.policyrenewal.notification;

import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import com.fulcrumdigital.policyrenewal.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPoller {

    private final ReminderOutboxRepository outboxRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final ThreadPoolTaskExecutor notificationExecutor;

    @Value("${notification.poll.batch-size:5}")
    private int batchSize;

    @Value("${notification.poll.worker-count:3}")
    private int workerCount;

    // Every few seconds, spin up `workerCount` workers that each grab their
    // own batch. Multiple workers never collide, because SKIP LOCKED means
    // each one only ever sees rows nobody else currently holds.
    @Scheduled(fixedDelayString = "${notification.poll.interval-ms:5000}")
    public void triggerPoll() {
        for (int i = 0; i < workerCount; i++) {
            notificationExecutor.execute(this::processBatch);
        }
    }

    // The entire fetch -> process sequence happens in ONE transaction, so
    // the row lock from FOR UPDATE SKIP LOCKED is held for the whole
    // duration, not just the initial read.
    @Transactional
    public void processBatch() {
        List<ReminderOutbox> batch =
                outboxRepository.findPendingBatchForUpdate(LocalDateTime.now(clock), batchSize);

        if (batch.isEmpty()) {
            return;
        }
        log.info("Worker picked up {} reminder(s)", batch.size());
        for (ReminderOutbox event : batch) {
            notificationService.processEvent(event);
        }
    }
}