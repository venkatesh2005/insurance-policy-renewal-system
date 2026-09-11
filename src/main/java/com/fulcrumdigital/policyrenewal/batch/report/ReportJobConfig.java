package com.fulcrumdigital.policyrenewal.batch.report;

import com.fulcrumdigital.policyrenewal.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

// Single aggregation pass — not per-item chunking — so a Tasklet is used
// instead of the reader/processor/writer pattern.
@Configuration
@RequiredArgsConstructor
public class ReportJobConfig {

    private final ReportService reportService;

    @Bean
    public Step reportStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("reportStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    reportService.generateAndWriteReport();
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }

    @Bean
    public Job renewalFunnelReportJob(JobRepository jobRepository, Step reportStep) {
        return new JobBuilder("renewalFunnelReportJob", jobRepository)
                .start(reportStep)
                .build();
    }
}
