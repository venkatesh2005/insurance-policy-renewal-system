package com.fulcrumdigital.policyrenewal.dto;

import com.fulcrumdigital.policyrenewal.entity.enums.PolicyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreatePolicyRequest {

    @NotBlank(message = "policyNumber is required")
    private String policyNumber;

    @NotBlank(message = "holderName is required")
    private String holderName;

    private String email;
    private String mobile;

    @NotNull(message = "policyType is required")
    private PolicyType policyType;

    @NotNull(message = "premiumAmount is required")
    @Positive(message = "premiumAmount must be positive")
    private BigDecimal premiumAmount;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;
}
