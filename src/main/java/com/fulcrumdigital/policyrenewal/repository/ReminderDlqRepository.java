package com.fulcrumdigital.policyrenewal.repository;

import com.fulcrumdigital.policyrenewal.entity.ReminderDlq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReminderDlqRepository extends JpaRepository<ReminderDlq, Long> {

    // All dead-lettered reminders for a specific policy.
    // Used by GET /dlq to show what's waiting for manual requeue.
    @Query("""
            SELECT d FROM ReminderDlq d
            WHERE d.policyNumber = :policyNumber
            ORDER BY d.movedAt DESC
            """)
    List<ReminderDlq> findByPolicyNumber(@Param("policyNumber") String policyNumber);

    // Funnel report: count of dead-lettered reminders per tier.
    @Query("SELECT COUNT(d) FROM ReminderDlq d WHERE d.tier = :tier")
    long countByTier(@Param("tier") String tier);
}
