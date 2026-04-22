package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class FinanceAccountMappingCreateRequest {
    @Positive
    private int companyId;

    @Positive
    private Integer branchId;

    @NotBlank
    private String mappingKey;

    @NotNull
    private UUID accountId;

    @Min(1)
    private int priority = 100;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @NotBlank
    private String status = "active";
}
