package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class FinanceJournalReversalRequest {
    @Positive
    private int companyId;

    @NotNull
    private LocalDate postingDate;

    @NotNull
    private UUID fiscalPeriodId;

    @NotBlank
    private String reason;

    @Min(1)
    private int version;
}
