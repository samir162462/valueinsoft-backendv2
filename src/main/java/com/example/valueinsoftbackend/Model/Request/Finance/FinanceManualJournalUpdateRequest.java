package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

@Data
public class FinanceManualJournalUpdateRequest {
    @Positive
    private int companyId;

    @Positive
    private Integer branchId;

    @NotNull
    private LocalDate postingDate;

    @NotNull
    private UUID fiscalPeriodId;

    @NotBlank
    private String description;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currencyCode = "EGP";

    @NotNull
    @DecimalMin(value = "0.00000001")
    private BigDecimal exchangeRate = BigDecimal.ONE;

    private boolean closingEntry = false;

    @NotEmpty
    @Valid
    private ArrayList<FinanceManualJournalLineRequest> lines = new ArrayList<>();

    @Min(1)
    private int version;
}
