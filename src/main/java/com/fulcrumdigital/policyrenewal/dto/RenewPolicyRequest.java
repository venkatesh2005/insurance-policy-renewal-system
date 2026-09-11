package com.fulcrumdigital.policyrenewal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RenewPolicyRequest {

    @NotBlank(message = "paymentReference is required")
    private String paymentReference;

    @NotNull(message = "newEndDate is required")
    private LocalDate newEndDate;
}