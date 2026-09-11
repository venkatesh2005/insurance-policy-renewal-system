package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyType;
import com.fulcrumdigital.policyrenewal.entity.enums.ReminderTier;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Requirement: "one test proving the detection job doesn't duplicate
// reminders on rerun."
//
// Must be an integration test, not a mocked unit test, because the
// no-duplicates guarantee is enforced by the database's unique constraint
// (uq_policy_tier_day) via a caught DataIntegrityViolationException in
// ReminderOutboxWriter — a mock repository cannot simulate a real
// constraint violation, so this test runs against an actual in-memory
// H2 database. @ActiveProfiles("test") loads application-test.yml which
// points at jdbc:h2:mem:... so it never touches the real dev database.
@SpringBootTest
@ActiveProfiles("test")
class ReminderServiceIntegrationTest {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ReminderOutboxRepository outboxRepository;

    @Test
    void createReminderIfNeeded_calledTwiceForSamePolicyTierAndDay_onlyCreatesOneRow() {
        // Unique suffix prevents policy number collisions across test runs
        String policyNumber = "POL-DUPE-TEST-" + System.nanoTime();

        Policy policy = policyRepository.save(Policy.builder()
                .policyNumber(policyNumber)
                .holderName("Duplicate Test User")
                .email("dupe@example.com")
                .policyType(PolicyType.MOTOR)
                .premiumAmount(new BigDecimal("1000"))
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().plusDays(15))
                .status(PolicyStatus.ACTIVE)
                .build());

        // --- Simulates Job 1 running twice on the same day ---
        ReminderService.WriteResult firstRun =
                reminderService.createReminderIfNeeded(policy, ReminderTier.DAY_15);
        ReminderService.WriteResult secondRun =
                reminderService.createReminderIfNeeded(policy, ReminderTier.DAY_15);

        // First run: created
        assertThat(firstRun).isEqualTo(ReminderService.WriteResult.CREATED);

        // Second run: skipped — unique constraint (policy_number, tier, reminder_date)
        // rejected the duplicate insert at the database level
        assertThat(secondRun).isEqualTo(ReminderService.WriteResult.SKIPPED_DUPLICATE);

        // Exactly one outbox row — never two
        int rowCount = outboxRepository
                .findByPolicyNumber(policyNumber)
                .size();
        assertThat(rowCount).isEqualTo(1);

        // Policy status was flipped to RENEWAL_DUE on first run
        Policy updated = policyRepository.findByPolicyNumber(policyNumber).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PolicyStatus.RENEWAL_DUE);
    }
}