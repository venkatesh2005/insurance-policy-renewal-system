package com.fulcrumdigital.policyrenewal.repository;

import com.fulcrumdigital.policyrenewal.entity.JobSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobScheduleRepository extends JpaRepository<JobSchedule, Long> {

    // Fetch the schedule row for a specific job by its name.
    // Used by BatchJobScheduler at startup and by JobScheduleService
    // when updating a cron expression or enabled flag via the API.
    @Query("SELECT j FROM JobSchedule j WHERE j.jobName = :jobName")
    Optional<JobSchedule> findByJobName(@Param("jobName") String jobName);

    // Fetch all enabled schedules — used by BatchJobScheduler at
    // startup to register only the jobs that should actually run.
    @Query("SELECT j FROM JobSchedule j WHERE j.enabled = true ORDER BY j.jobName ASC")
    List<JobSchedule> findAllEnabled();
}
