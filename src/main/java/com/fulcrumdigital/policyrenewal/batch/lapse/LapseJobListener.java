package com.fulcrumdigital.policyrenewal.batch.lapse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

@Slf4j
public class LapseJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LapseRunStats.reset();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("Job2 (Lapse) summary: lapsed={} skipped={}",
                LapseRunStats.getLapsed(), LapseRunStats.getSkipped());
    }
}
