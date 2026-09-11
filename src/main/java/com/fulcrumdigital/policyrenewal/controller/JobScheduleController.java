package com.fulcrumdigital.policyrenewal.controller;

import com.fulcrumdigital.policyrenewal.entity.JobSchedule;
import com.fulcrumdigital.policyrenewal.service.JobScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class JobScheduleController {

    private final JobScheduleService jobScheduleService;

    // View all job schedules stored in the database.
    // GET /schedules
    @GetMapping
    public List<JobSchedule> getAllSchedules() {
        return jobScheduleService.getAllSchedules();
    }

    // Change a job's cron expression — takes effect immediately, no restart needed.
    // PUT /schedules/renewalDetectionJob/cron
    // Body: { "cronExpr": "0 */2 * * * *" }
    @PutMapping("/{jobName}/cron")
    public JobSchedule updateCron(
            @PathVariable String jobName,
            @RequestBody Map<String, String> body) {
        return jobScheduleService.updateCron(jobName, body.get("cronExpr"));
    }

    // Enable or disable a job.
    // PUT /schedules/renewalDetectionJob/enabled
    // Body: { "enabled": false }
    @PutMapping("/{jobName}/enabled")
    public JobSchedule setEnabled(
            @PathVariable String jobName,
            @RequestBody Map<String, Boolean> body) {
        return jobScheduleService.setEnabled(jobName, body.get("enabled"));
    }
}
