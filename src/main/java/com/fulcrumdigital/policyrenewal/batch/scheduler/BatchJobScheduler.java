package com.fulcrumdigital.policyrenewal.batch.scheduler;

import com.fulcrumdigital.policyrenewal.entity.JobSchedule;
import com.fulcrumdigital.policyrenewal.repository.JobScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;

// Dynamic scheduler: reads cron expressions from the job_schedule
// database table at startup, so schedules can be changed at runtime
// via PUT /schedules/{jobName}/cron without restarting the app.
// Implements SchedulingConfigurer rather than using @Scheduled(cron)
// annotations, because @Scheduled cron values are fixed at startup
// and cannot be changed at runtime.
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobScheduler implements SchedulingConfigurer {

    private final JobLauncher jobLauncher;
    private final Job renewalDetectionJob;
    private final Job policyLapseJob;
    private final Job renewalFunnelReportJob;
    private final JobScheduleRepository jobScheduleRepository;
    private final TaskScheduler taskScheduler;

    private Map<String, Job> jobMap() {
        return Map.of(
                "renewalDetectionJob",    renewalDetectionJob,
                "policyLapseJob",         policyLapseJob,
                "renewalFunnelReportJob", renewalFunnelReportJob
        );
    }

    // Called once at application startup by Spring's scheduling
    // infrastructure. Reads all rows from job_schedule and registers
    // each enabled job with its cron expression from the database.
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Map<String, Job> jobs = jobMap();

        jobScheduleRepository.findAll().forEach(schedule -> {
            if (!schedule.isEnabled()) {
                log.info("Job {} is disabled in DB — not scheduling", schedule.getJobName());
                return;
            }

            Job job = jobs.get(schedule.getJobName());
            if (job == null) {
                log.warn("No Job bean found for '{}' in DB — skipping", schedule.getJobName());
                return;
            }

            registrar.addTriggerTask(
                    () -> launch(job, schedule.getJobName()),
                    ctx -> new CronTrigger(schedule.getCronExpr()).nextExecution(ctx)
            );

            log.info("Scheduled '{}' with cron '{}' (from DB)",
                    schedule.getJobName(), schedule.getCronExpr());
        });
    }

    // Called by JobScheduleService after an API update — reschedules
    // one job immediately with the new cron, so the change takes
    // effect without restarting the app.
    public void reschedule(String jobName, String newCronExpr) {
        Job job = jobMap().get(jobName);
        if (job == null) {
            log.warn("Cannot reschedule '{}' — no matching Job bean", jobName);
            return;
        }
        taskScheduler.schedule(() -> launch(job, jobName), new CronTrigger(newCronExpr));
        log.info("Rescheduled '{}' with new cron '{}' (live, no restart needed)",
                jobName, newCronExpr);
    }

    private void launch(Job job, String label) {
        try {
            var params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(job, params);
            log.info("Scheduled trigger fired: {}", label);
        } catch (Exception e) {
            log.error("Scheduled job '{}' failed to launch: {}", label, e.getMessage());
        }
    }
}
