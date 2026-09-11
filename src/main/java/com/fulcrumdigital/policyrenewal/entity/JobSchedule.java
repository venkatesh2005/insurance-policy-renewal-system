package com.fulcrumdigital.policyrenewal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Stores the cron expression and enabled flag for each batch job.
// Loaded at startup to configure the scheduler; updated at runtime via
// PUT /schedules/{jobName}/cron or PUT /schedules/{jobName}/enabled
// so schedules can change without restarting the app.
@Entity
@Table(name = "job_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Must exactly match the Spring Batch Job bean name —
    // used to look up the right Job to reschedule.
    @Column(name = "job_name", nullable = false, unique = true, length = 100)
    private String jobName;

    // Standard 6-field Spring cron: second minute hour day month weekday
    @Column(name = "cron_expr", nullable = false, length = 100)
    private String cronExpr;

    // When false, the job is not registered in the scheduler at all.
    @Column(nullable = false)
    private boolean enabled;

    // Human-readable note — no functional purpose, makes GET /schedules readable.
    @Column(length = 300)
    private String description;

    // Automatically updated by Hibernate whenever the row changes.
    // Lets you see when someone last changed the schedule.
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
