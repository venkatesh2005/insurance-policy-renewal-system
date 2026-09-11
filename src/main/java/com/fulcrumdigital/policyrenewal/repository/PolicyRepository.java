package com.fulcrumdigital.policyrenewal.repository;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    // Fetch a single policy by its client-facing unique number.
    // Used by every API endpoint and by the renew/lapse logic.
    @Query("SELECT p FROM Policy p WHERE p.policyNumber = :policyNumber")
    Optional<Policy> findByPolicyNumber(@Param("policyNumber") String policyNumber);

    // Idempotency check: does a policy with this number already exist?
    @Query("SELECT COUNT(p) > 0 FROM Policy p WHERE p.policyNumber = :policyNumber")
    boolean existsByPolicyNumber(@Param("policyNumber") String policyNumber);

    // Job 1 reader: scans ACTIVE + RENEWAL_DUE policies expiring within
    // the detection window. Both statuses must be included — once a policy
    // receives its first reminder it becomes RENEWAL_DUE; excluding it
    // would mean only the first tier ever fires and the 15_DAY, 7_DAY,
    // and OVERDUE reminders would never be created.
    @Query("""
            SELECT p FROM Policy p
            WHERE p.status IN :statuses
            AND   p.endDate <= :cutoffDate
            ORDER BY p.endDate ASC
            """)
    List<Policy> findActivePoliciesForDetection(
            @Param("statuses") List<PolicyStatus> statuses,
            @Param("cutoffDate") LocalDate cutoffDate);

    // Job 2 reader: finds RENEWAL_DUE policies past end date + grace period.
    // PESSIMISTIC_WRITE locks every returned row so a concurrent renewal
    // that arrives while the lapse job is processing cannot slip past
    // undetected — whichever transaction touches the row first wins.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Policy p
            WHERE p.status = 'RENEWAL_DUE'
            AND   p.endDate <= :cutoffDate
            ORDER BY p.endDate ASC
            """)
    List<Policy> findLapseCandidates(@Param("cutoffDate") LocalDate cutoffDate);

    // Funnel report: count of policies currently in a given status.
    @Query("SELECT COUNT(p) FROM Policy p WHERE p.status = :status")
    long countPoliciesByStatus(@Param("status") PolicyStatus status);

    // Funnel report: count of policies renewed on a specific date.
    @Query("SELECT COUNT(p) FROM Policy p WHERE p.lastRenewedAt = :date")
    long countPoliciesRenewedOn(@Param("date") LocalDate date);
}
