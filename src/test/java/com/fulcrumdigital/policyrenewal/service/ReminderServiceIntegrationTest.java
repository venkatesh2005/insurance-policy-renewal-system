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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReminderServiceIntegrationTest {

    @Autowired private ReminderService reminderService;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private ReminderOutboxRepository outboxRepository;

    @Test
    void createReminderIfNeeded_calledTwiceForSamePolicyTierAndDay_doesNotCreateADuplicateRow() {
        Policy policy = policyRepository.save(Policy.builder()
                .policyNumber("POL-DUP-TEST-" + System.nanoTime())
                .holderName("Test Holder")
                .email("test@example.com")
                .policyType(PolicyType.MOTOR)
                .premiumAmount(new BigDecimal("1000"))
                .startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().plusDays(15))
                .status(PolicyStatus.ACTIVE)
                .build());

        ReminderService.WriteResult firstRun  = reminderService.createReminderIfNeeded(policy, ReminderTier.DAY_15);
        ReminderService.WriteResult secondRun = reminderService.createReminderIfNeeded(policy, ReminderTier.DAY_15);

        assertThat(firstRun).isEqualTo(ReminderService.WriteResult.CREATED);
        assertThat(secondRun).isEqualTo(ReminderService.WriteResult.SKIPPED_DUPLICATE);

        int count = outboxRepository.findByPolicyNumberOrderByCreatedAtDesc(policy.getPolicyNumber()).size();
        assertThat(count).isEqualTo(1);
    }
}
