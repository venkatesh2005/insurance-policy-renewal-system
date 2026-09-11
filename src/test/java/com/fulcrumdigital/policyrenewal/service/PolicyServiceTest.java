package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.dto.CreatePolicyRequest;
import com.fulcrumdigital.policyrenewal.dto.PolicyResponse;
import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyType;
import com.fulcrumdigital.policyrenewal.repository.NotificationLogRepository;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Requirement: "one test proving the create API is idempotent."
// Pure Mockito unit test — no Spring context, no real database.
// The idempotency rule is plain branching logic in PolicyService
// that does not need a real DB to verify.
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private Clock clock;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private PolicyRenewalExecutor policyRenewalExecutor;

    @InjectMocks
    private PolicyService policyService;

    // ---------------------------------------------------------------
    // Test 1: duplicate policyNumber → returns existing, never saves
    // ---------------------------------------------------------------
    @Test
    void createPolicy_whenPolicyNumberAlreadyExists_returnsDuplicateAndDoesNotSaveAgain() {
        Policy existing = Policy.builder()
                .policyNumber("POL-001")
                .holderName("Ramesh Kumar")
                .policyType(PolicyType.MOTOR)
                .premiumAmount(new BigDecimal("5000"))
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2026, 1, 1))
                .status(PolicyStatus.ACTIVE)
                .build();

        when(policyRepository.findByPolicyNumber("POL-001"))
                .thenReturn(Optional.of(existing));

        CreatePolicyRequest request = buildRequest(
                "POL-001", "Ramesh Kumar", PolicyType.MOTOR,
                new BigDecimal("5000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

        PolicyResponse response = policyService.createPolicy(request);

        // Core assertion: duplicate flag set, no new row written
        assertThat(response.isDuplicate()).isTrue();
        assertThat(response.getPolicyNumber()).isEqualTo("POL-001");
        assertThat(response.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        verify(policyRepository, never()).save(any(Policy.class));
    }

    // ---------------------------------------------------------------
    // Test 2: new policyNumber → creates ACTIVE policy, saves exactly once
    // ---------------------------------------------------------------
    @Test
    void createPolicy_whenPolicyNumberIsNew_createsActivePolicyAndSavesOnce() {
        when(policyRepository.findByPolicyNumber("POL-002"))
                .thenReturn(Optional.empty());
        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreatePolicyRequest request = buildRequest(
                "POL-002", "Asha Patel", PolicyType.HEALTH,
                new BigDecimal("3000"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        PolicyResponse response = policyService.createPolicy(request);

        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(response.getPolicyNumber()).isEqualTo("POL-002");
        verify(policyRepository, times(1)).save(any(Policy.class));
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------
    private CreatePolicyRequest buildRequest(String policyNumber, String holderName,
                                             PolicyType type, BigDecimal premium, LocalDate start, LocalDate end) {
        CreatePolicyRequest r = new CreatePolicyRequest();
        r.setPolicyNumber(policyNumber);
        r.setHolderName(holderName);
        r.setPolicyType(type);
        r.setPremiumAmount(premium);
        r.setStartDate(start);
        r.setEndDate(end);
        return r;
    }
}