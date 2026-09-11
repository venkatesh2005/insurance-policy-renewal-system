package com.fulcrumdigital.policyrenewal.entity;

import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 50)
    private String policyNumber;

    @Column(name = "holder_name", nullable = false, length = 200)
    private String holderName;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 20)
    private PolicyType policyType;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "needs_manual_follow_up", nullable = false)
    @Builder.Default
    private boolean needsManualFollowUp = false;

    // Optimistic locking — resolves the renew-vs-lapse race condition.
    @Version
    @Column(nullable = false)
    private Long version;

    // Set whenever the policy is renewed — used by the funnel report
    // to count "RENEWED in the period."
    @Column(name = "last_renewed_at")
    private LocalDate lastRenewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
