package com.fulcrumdigital.policyrenewal.repository;

import com.fulcrumdigital.policyrenewal.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    // Full notification attempt history for a policy, newest first.
    // Used by GET /policies/{policyNumber} to build the reminderHistory
    // section of PolicyResponse.
    @Query("""
            SELECT n FROM NotificationLog n
            WHERE n.policyNumber = :policyNumber
            ORDER BY n.attemptedAt DESC
            """)
    List<NotificationLog> findByPolicyNumber(@Param("policyNumber") String policyNumber);

    // Funnel report: count of notification attempts with a specific
    // outcome for a specific reminder tier.
    @Query("""
            SELECT COUNT(n) FROM NotificationLog n
            WHERE n.tier = :tier
            AND   n.outcome = :outcome
            """)
    long countByTierAndOutcome(
            @Param("tier") String tier,
            @Param("outcome") String outcome);
}
