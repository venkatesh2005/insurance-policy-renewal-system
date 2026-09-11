package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.dto.CreatePolicyRequest;
import com.fulcrumdigital.policyrenewal.dto.PolicyResponse;
import com.fulcrumdigital.policyrenewal.dto.ReminderHistoryItem;
import com.fulcrumdigital.policyrenewal.dto.RenewPolicyRequest;
import com.fulcrumdigital.policyrenewal.entity.NotificationLog;
import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.exception.InvalidPolicyException;
import com.fulcrumdigital.policyrenewal.exception.PolicyNotFoundException;
import com.fulcrumdigital.policyrenewal.repository.NotificationLogRepository;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final Clock clock;
    private final NotificationLogRepository notificationLogRepository;
    private final PolicyRenewalExecutor policyRenewalExecutor;

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        var existing = policyRepository.findByPolicyNumber(request.getPolicyNumber());
        if (existing.isPresent()) {
            log.info("Duplicate create request for policy {}", request.getPolicyNumber());
            return mapToResponse(existing.get(), true);
        }

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new InvalidPolicyException("endDate must be after startDate");
        }

        Policy policy = Policy.builder()
                .policyNumber(request.getPolicyNumber())
                .holderName(request.getHolderName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .policyType(request.getPolicyType())
                .premiumAmount(request.getPremiumAmount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(PolicyStatus.ACTIVE)
                .build();

        Policy saved = policyRepository.save(policy);
        log.info("Created policy {} with status ACTIVE", saved.getPolicyNumber());
        return mapToResponse(saved, false);
    }

    // Delegates the actual transactional write to PolicyRenewalExecutor
    // (a separate bean) so that the retry-on-optimistic-lock-conflict
    // logic goes through Spring's transaction proxy correctly.
    // Calling an @Transactional method on `this` from within the same
    // class bypasses the proxy entirely (the "self-invocation" pitfall).
    public PolicyResponse renewPolicy(String policyNumber, RenewPolicyRequest request) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new PolicyNotFoundException(policyNumber));

        try {
            Policy renewed = policyRenewalExecutor.renew(policy, request);
            return mapToResponse(renewed, false);
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.info("Policy {} had a concurrent update — retrying renewal once", policyNumber);
            Policy fresh = policyRepository.findByPolicyNumber(policyNumber)
                    .orElseThrow(() -> new PolicyNotFoundException(policyNumber));
            Policy renewed = policyRenewalExecutor.renew(fresh, request);
            return mapToResponse(renewed, false);
        }
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new PolicyNotFoundException(policyNumber));

        List<ReminderHistoryItem> history = notificationLogRepository
                .findByPolicyNumber(policyNumber)
                .stream()
                .map(this::mapToHistoryItem)
                .toList();

        PolicyResponse response = mapToResponse(policy, false);
        response.setReminderHistory(history);
        return response;
    }

    @Transactional
    public boolean lapseIfStillEligible(Policy policy, LocalDate cutoffDate) {
        if (policy.getStatus() != PolicyStatus.RENEWAL_DUE) return false;
        if (policy.getEndDate().isAfter(cutoffDate))        return false;

        try {
            policy.setStatus(PolicyStatus.LAPSED);
            policyRepository.saveAndFlush(policy);
            return true;
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.info("Policy {} was renewed concurrently — skipping lapse",
                    policy.getPolicyNumber());
            return false;
        }
    }

    private PolicyResponse mapToResponse(Policy policy, boolean duplicate) {
        return PolicyResponse.builder()
                .policyNumber(policy.getPolicyNumber())
                .holderName(policy.getHolderName())
                .email(policy.getEmail())
                .mobile(policy.getMobile())
                .policyType(policy.getPolicyType())
                .premiumAmount(policy.getPremiumAmount())
                .startDate(policy.getStartDate())
                .endDate(policy.getEndDate())
                .status(policy.getStatus())
                .needsManualFollowUp(policy.isNeedsManualFollowUp())
                .duplicate(duplicate)
                .build();
    }

    private ReminderHistoryItem mapToHistoryItem(NotificationLog entry) {
        return ReminderHistoryItem.builder()
                .tier(entry.getTier())
                .channel(entry.getChannel())
                .attemptNumber(entry.getAttemptNumber())
                .outcome(entry.getOutcome())
                .errorMessage(entry.getErrorMessage())
                .attemptedAt(entry.getAttemptedAt())
                .build();
    }
}
