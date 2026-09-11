package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.dto.RenewPolicyRequest;
import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.repository.ReminderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

// Split out from PolicyService deliberately so that the retry-on-
// optimistic-lock-conflict logic in PolicyService.renewPolicy goes
// through Spring's transaction proxy correctly. Calling an @Transactional
// method on `this` within the same class bypasses the proxy entirely
// (the "self-invocation" pitfall), so the retry would silently not
// run in a new transaction if this method lived inside PolicyService.
@Service
@RequiredArgsConstructor
@Slf4j
class PolicyRenewalExecutor {

    private final PolicyRepository policyRepository;
    private final ReminderOutboxRepository outboxRepository;
    private final Clock clock;

    @Transactional
    public Policy renew(Policy policy, RenewPolicyRequest request) {
        String policyNumber     = policy.getPolicyNumber();
        PolicyStatus prevStatus = policy.getStatus();

        policy.setEndDate(request.getNewEndDate());
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setNeedsManualFollowUp(false);
        policy.setLastRenewedAt(LocalDate.now(clock));

        // saveAndFlush so a version conflict throws immediately
        // inside this transaction — not deferred to commit time,
        // where it would be outside PolicyService's try/catch.
        policyRepository.saveAndFlush(policy);

        log.info("Policy {} renewed: {} -> ACTIVE, new endDate={}, paymentRef={}",
                policyNumber, prevStatus, request.getNewEndDate(), request.getPaymentReference());

        // Bulk UPDATE — one query instead of SELECT + N saves.
        // Cancels every PENDING reminder so the customer doesn't
        // keep receiving reminders after they've already paid.
        int cancelled = outboxRepository.cancelPendingReminders(policyNumber);
        log.info("Cancelled {} pending reminder(s) for policy {}", cancelled, policyNumber);

        return policy;
    }
}
