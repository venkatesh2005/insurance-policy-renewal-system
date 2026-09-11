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
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

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

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "attempted_at", updatable = false)
    private LocalDateTime attemptedAt;
}