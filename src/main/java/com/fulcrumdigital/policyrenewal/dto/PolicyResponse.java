package com.fulcrumdigital.policyrenewal.dto;

import com.fulcrumdigital.policyrenewal.entity.enums.PolicyStatus;
import com.fulcrumdigital.policyrenewal.entity.enums.PolicyType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class PolicyResponse {
    private String policyNumber;
    private String holderName;
    private String email;
    private String mobile;
    private PolicyType policyType;
    private BigDecimal premiumAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private PolicyStatus status;
    private boolean needsManualFollowUp;
    private boolean duplicate;
    private List<ReminderHistoryItem> reminderHistory;
}
