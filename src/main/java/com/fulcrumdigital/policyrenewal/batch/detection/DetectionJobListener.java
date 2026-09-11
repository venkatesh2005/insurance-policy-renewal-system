package com.fulcrumdigital.policyrenewal.batch.detection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

@Slf4j
public class DetectionJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        DetectionRunStats.reset();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        StringBuilder summary = new StringBuilder("Job1 (Detection) summary: ");
        DetectionRunStats.getCreatedByTier().forEach((tier, count) ->
                summary.append("created[").append(tier).append("]=").append(count.get()).append(" "));
        summary.append("skipped=").append(DetectionRunStats.getSkipped());
        log.info(summary.toString());
    }
}
