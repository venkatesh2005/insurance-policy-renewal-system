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

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private Clock clock;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private PolicyRenewalExecutor policyRenewalExecutor;

    @InjectMocks
    private PolicyService policyService;

    @Test
    void createPolicy_whenPolicyNumberAlreadyExists_returnsDuplicateAndDoesNotSaveAgain() {
        Policy existing = Policy.builder()
                .policyNumber("POL-1001")
                .holderName("Ramesh Kumar")
                .policyType(PolicyType.MOTOR)
                .premiumAmount(new BigDecimal("5000"))
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2026, 1, 1))
                .status(PolicyStatus.ACTIVE)
                .build();

        when(policyRepository.findByPolicyNumber("POL-1001")).thenReturn(Optional.of(existing));

        CreatePolicyRequest request = new CreatePolicyRequest();
        request.setPolicyNumber("POL-1001");
        request.setHolderName("Ramesh Kumar");
        request.setPolicyType(PolicyType.MOTOR);
        request.setPremiumAmount(new BigDecimal("5000"));
        request.setStartDate(LocalDate.of(2025, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 1));

        PolicyResponse response = policyService.createPolicy(request);

        assertThat(response.isDuplicate()).isTrue();
        assertThat(response.getPolicyNumber()).isEqualTo("POL-1001");
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void createPolicy_whenPolicyNumberIsNew_createsActivePolicyAndSavesOnce() {
        when(policyRepository.findByPolicyNumber("POL-2002")).thenReturn(Optional.empty());
        when(policyRepository.save(any(Policy.class))).thenAnswer(i -> i.getArgument(0));

        CreatePolicyRequest request = new CreatePolicyRequest();
        request.setPolicyNumber("POL-2002");
        request.setHolderName("Asha Patel");
        request.setPolicyType(PolicyType.HEALTH);
        request.setPremiumAmount(new BigDecimal("3000"));
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2027, 1, 1));

        PolicyResponse response = policyService.createPolicy(request);

        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        verify(policyRepository).save(any(Policy.class));
    }
}
