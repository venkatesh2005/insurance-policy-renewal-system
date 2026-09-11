package com.fulcrumdigital.policyrenewal.repository;

import com.fulcrumdigital.policyrenewal.entity.ReminderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderOutboxRepository extends JpaRepository<ReminderOutbox, Long> {

    // Full reminder history for a policy — used by GET /policies/{policyNumber}
    // to build the reminderHistory list in PolicyResponse.
    @Query("""
            SELECT r FROM ReminderOutbox r
            WHERE r.policyNumber = :policyNumber
            ORDER BY r.createdAt DESC
            """)
    List<ReminderOutbox> findByPolicyNumber(@Param("policyNumber") String policyNumber);

    // Finds PENDING reminders for a specific policy — used by renewPolicy
    // to cancel any reminders still waiting to be sent after a renewal.
    @Query("""
            SELECT r FROM ReminderOutbox r
            WHERE r.policyNumber = :policyNumber
            AND   r.status = :status
            """)
    List<ReminderOutbox> findByPolicyNumberAndStatus(
            @Param("policyNumber") String policyNumber,
            @Param("status") String status);

    // Poller pickup query: fetches a locked batch of PENDING events whose
    // retry time has passed. FOR UPDATE SKIP LOCKED ensures multiple
    // concurrent poller workers never pick up the same row — each worker
    // only sees rows not currently locked by another transaction.
    // nativeQuery=true is required because SKIP LOCKED is a raw SQL
    // clause with no JPQL equivalent.
    @Query(value = """
            SELECT * FROM reminder_outbox
            WHERE  status = 'PENDING'
            AND    next_retry_at <= :now
            ORDER BY created_at
            LIMIT  :batchSize
            FOR UPDATE SKIP LOCKED
            """,
           nativeQuery = true)
    List<ReminderOutbox> findPendingBatchForUpdate(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize);

    // Bulk-cancel all PENDING reminders for a policy in one UPDATE
    // instead of fetching each row and saving individually.
    // Used by renewPolicy — cheaper than a SELECT + N saves.
    @Modifying
    @Query("""
            UPDATE ReminderOutbox r
            SET    r.status = 'CANCELLED'
            WHERE  r.policyNumber = :policyNumber
            AND    r.status = 'PENDING'
            """)
    int cancelPendingReminders(@Param("policyNumber") String policyNumber);
}
