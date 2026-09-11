package com.fulcrumdigital.policyrenewal.batch.detection;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.service.ReminderService;
import com.fulcrumdigital.policyrenewal.service.ReminderTierCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DetectionJobConfig {

    private final PolicyRepository policyRepository;
    private final ReminderService reminderService;
    private final ReminderTierCalculator tierCalculator;
    private final Clock clock;

    @Value("${renewal.detection.window-days:30}")
    private int windowDays;

    @Value("${renewal.detection.chunk-size:10}")
    private int chunkSize;

    // ---------- READER ----------
    // Scans ACTIVE + RENEWAL_DUE policies expiring within the window.
    // Why both statuses: once a policy receives its first reminder
    // (30_DAY), it flips to RENEWAL_DUE. If we only scanned ACTIVE,
    // it would never be picked up again — and its 15_DAY, 7_DAY,
    // and OVERDUE reminders would never fire. The outbox unique
    // constraint (policy_number, tier, reminder_date) ensures no
    // tier fires twice on the same day, so including RENEWAL_DUE
    // is completely safe.
    @Bean
    @Scope(value = "step", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public ItemReader<Policy> detectionReader() {
        LocalDate today  = LocalDate.now(clock);
        LocalDate cutoff = today.plusDays(windowDays);

        List<Policy> candidates = policyRepository.findActivePoliciesForDetection(
                List.of(PolicyStatus.ACTIVE, PolicyStatus.RENEWAL_DUE), cutoff);

        log.info("Detection job: scanning {} policies (ACTIVE + RENEWAL_DUE) with endDate <= {}",
                candidates.size(), cutoff);

        return new ListItemReader<>(candidates);
    }

    // ---------- PROCESSOR ----------
    // Calculates which tier applies today. Returns null to skip a
    // policy if it doesn't fall into any tier window yet (>30 days out).
    @Bean
    public ItemProcessor<Policy, ReminderCandidate> detectionProcessor() {
        return policy -> {
            LocalDate today = LocalDate.now(clock);
            String tier = tierCalculator.calculateTier(policy.getEndDate(), today);
            if (tier == null) return null;
            return new ReminderCandidate(policy, tier);
        };
    }

    // ---------- WRITER ----------
    @Bean
    public ItemWriter<ReminderCandidate> detectionWriter() {
        return chunk -> {
            for (ReminderCandidate candidate : chunk) {
                var result = reminderService.createReminderIfNeeded(
                        candidate.getPolicy(), candidate.getTier());
                if (result == ReminderService.WriteResult.CREATED) {
                    DetectionRunStats.recordCreated(candidate.getTier());
                } else {
                    DetectionRunStats.recordSkipped();
                }
            }
        };
    }

    // ---------- STEP ----------
    @Bean
    public Step detectionStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("detectionStep", jobRepository)
                .<Policy, ReminderCandidate>chunk(chunkSize, txManager)
                .reader(detectionReader())
                .processor(detectionProcessor())
                .writer(detectionWriter())
                .build();
    }

    // ---------- JOB ----------
    @Bean
    public Job renewalDetectionJob(JobRepository jobRepository, Step detectionStep) {
        return new JobBuilder("renewalDetectionJob", jobRepository)
                .listener(new DetectionJobListener())
                .start(detectionStep)
                .build();
    }
}
