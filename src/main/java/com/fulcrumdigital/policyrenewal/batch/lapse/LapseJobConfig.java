package com.fulcrumdigital.policyrenewal.batch.lapse;

import com.fulcrumdigital.policyrenewal.entity.Policy;
import com.fulcrumdigital.policyrenewal.repository.PolicyRepository;
import com.fulcrumdigital.policyrenewal.service.PolicyService;
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
public class LapseJobConfig {

    private final PolicyRepository policyRepository;
    private final PolicyService policyService;
    private final Clock clock;

    @Value("${renewal.lapse.grace-period-days:15}")
    private int gracePeriodDays;

    @Value("${renewal.lapse.chunk-size:10}")
    private int chunkSize;

    @Bean
    @Scope(value = "step", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public ItemReader<Policy> lapseReader() {
        LocalDate cutoffDate = LocalDate.now(clock).minusDays(gracePeriodDays);
        List<Policy> candidates = policyRepository.findLapseCandidates(cutoffDate);
        log.info("Lapse job: found {} RENEWAL_DUE policies with endDate <= {} (grace period {} days)",
                candidates.size(), cutoffDate, gracePeriodDays);
        return new ListItemReader<>(candidates);
    }

    @Bean
    public ItemProcessor<Policy, Policy> lapseProcessor() {
        return policy -> policy;
    }

    @Bean
    public ItemWriter<Policy> lapseWriter() {
        return chunk -> {
            LocalDate cutoffDate = LocalDate.now(clock).minusDays(gracePeriodDays);
            for (Policy policy : chunk) {
                boolean lapsed = policyService.lapseIfStillEligible(policy, cutoffDate);
                if (lapsed) LapseRunStats.recordLapsed();
                else        LapseRunStats.recordSkipped();
            }
        };
    }

    @Bean
    public Step lapseStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("lapseStep", jobRepository)
                .<Policy, Policy>chunk(chunkSize, txManager)
                .reader(lapseReader())
                .processor(lapseProcessor())
                .writer(lapseWriter())
                .build();
    }

    @Bean
    public Job policyLapseJob(JobRepository jobRepository, Step lapseStep) {
        return new JobBuilder("policyLapseJob", jobRepository)
                .listener(new LapseJobListener())
                .start(lapseStep)
                .build();
    }
}
