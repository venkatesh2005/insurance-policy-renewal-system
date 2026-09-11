package com.fulcrumdigital.policyrenewal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_dlq")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderDlq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    @Column(name = "policy_number", nullable = false, length = 50)
    private String policyNumber;

    @Column(nullable = false, length = 20)
    private String tier;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 1000)
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", nullable = false, length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "moved_at", updatable = false)
    private LocalDateTime movedAt;
}