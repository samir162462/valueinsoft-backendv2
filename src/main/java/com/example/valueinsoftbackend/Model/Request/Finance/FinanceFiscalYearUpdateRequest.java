package com.example.valueinsoftbackend.Model.Request.Finance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FinanceFiscalYearUpdateRequest {
    @Positive
    private int companyId;

    @NotBlank
    private String name;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @Pattern(regexp = "^[A-Z]{3}$")
    private String baseCurrencyCode = "EGP";

    @NotBlank
    private String status;

    @Min(1)
    private int version;
}
