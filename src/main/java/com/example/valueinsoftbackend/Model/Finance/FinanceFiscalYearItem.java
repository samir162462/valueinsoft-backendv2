package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceFiscalYearItem {
    private UUID fiscalYearId;
    private int companyId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String baseCurrencyCode;
    private String status;
    private int version;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
