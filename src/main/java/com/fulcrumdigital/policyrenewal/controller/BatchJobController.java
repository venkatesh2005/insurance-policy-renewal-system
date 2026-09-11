package com.fulcrumdigital.policyrenewal.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class BatchJobController {

    private final JobLauncher jobLauncher;
    private final Job renewalDetectionJob;
    private final Job policyLapseJob;
    private final Job renewalFunnelReportJob;


    @PostMapping("/detection/run")
    public String runDetectionJob() throws Exception {
        var params = new JobParametersBuilder()
                .addLong("runAt", System.currentTimeMillis()) // makes each run unique
                .toJobParameters();

        jobLauncher.run(renewalDetectionJob, params);
        return "Detection job triggered";
    }

    @PostMapping("/lapse/run")
    public String runLapseJob() throws Exception {
        var params = new JobParametersBuilder()
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(policyLapseJob, params);
        return "Lapse job triggered";
    }

    @PostMapping("/report/run")
    public String runReportJob() throws Exception {
        var params = new JobParametersBuilder()
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(renewalFunnelReportJob, params);
        return "Renewal funnel report generated";
    }
}