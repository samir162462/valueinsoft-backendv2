package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class FinanceTaxCodeCreateRequest {
    @Positive
    private int companyId;

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotNull
    @DecimalMin(value = "0.000000")
    @Digits(integer = 3, fraction = 6)
    private BigDecimal rate = BigDecimal.ZERO;

    @NotBlank
    private String taxType;

    private UUID outputAccountId;

    private UUID inputAccountId;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @NotBlank
    private String status = "active";
}
