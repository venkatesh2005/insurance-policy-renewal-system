package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private final PolicyRepository policyRepository;
    private final ReminderOutboxWriter outboxWriter;

    public enum WriteResult { CREATED, SKIPPED_DUPLICATE }

    @Transactional
    public WriteResult createReminderIfNeeded(Policy policy, String tier) {
        if (policy.getStatus() != PolicyStatus.RENEWAL_DUE) {
            policy.setStatus(PolicyStatus.RENEWAL_DUE);
            policyRepository.save(policy);
        }

        try {
            outboxWriter.insert(policy, tier);
            return WriteResult.CREATED;
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipped duplicate reminder for policy {} tier {} (already exists today)",
                    policy.getPolicyNumber(), tier);
            return WriteResult.SKIPPED_DUPLICATE;
        }
    }
}
