package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class FinanceFiscalPeriodUpdateRequest {
    @Positive
    private int companyId;

    @NotNull
    private UUID fiscalYearId;

    @Min(1)
    private int periodNumber;

    @NotBlank
    private String name;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotBlank
    private String status;

    @Min(1)
    private int version;
}
