package com.fulcrumdigital.policyrenewal.service;

import com.fulcrumdigital.policyrenewal.batch.scheduler.BatchJobScheduler;
import com.fulcrumdigital.policyrenewal.entity.JobSchedule;
import com.fulcrumdigital.policyrenewal.exception.PolicyNotFoundException;
import com.fulcrumdigital.policyrenewal.repository.JobScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScheduleService {

    private final JobScheduleRepository jobScheduleRepository;
    private final BatchJobScheduler batchJobScheduler;

    // Returns all job schedules currently stored in the database.
    public List<JobSchedule> getAllSchedules() {
        return jobScheduleRepository.findAll();
    }

    // Updates the cron expression for a job in the database,
    // then immediately reschedules the job in the running scheduler
    // so the change takes effect without restarting the app.
    @Transactional
    public JobSchedule updateCron(String jobName, String newCronExpr) {
        JobSchedule schedule = jobScheduleRepository.findByJobName(jobName)
                .orElseThrow(() -> new PolicyNotFoundException(
                        "No schedule found for job: " + jobName));

        schedule.setCronExpr(newCronExpr);
        JobSchedule saved = jobScheduleRepository.save(schedule);

        if (schedule.isEnabled()) {
            batchJobScheduler.reschedule(jobName, newCronExpr);
        }

        log.info("Updated cron for '{}' to '{}' and applied immediately", jobName, newCronExpr);
        return saved;
    }

    // Enables or disables a job. Persists to DB so the change
    // survives a restart (the scheduler reads from DB at startup).
    @Transactional
    public JobSchedule setEnabled(String jobName, boolean enabled) {
        JobSchedule schedule = jobScheduleRepository.findByJobName(jobName)
                .orElseThrow(() -> new PolicyNotFoundException(
                        "No schedule found for job: " + jobName));

        schedule.setEnabled(enabled);
        JobSchedule saved = jobScheduleRepository.save(schedule);

        log.info("Job '{}' {}", jobName, enabled ? "enabled" : "disabled");
        return saved;
    }
}
