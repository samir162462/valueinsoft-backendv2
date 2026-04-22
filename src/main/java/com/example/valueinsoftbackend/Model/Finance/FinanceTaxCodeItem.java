package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceTaxCodeItem {
    private UUID taxCodeId;
    private int companyId;
    private String code;
    private String name;
    private BigDecimal rate;
    private String taxType;
    private UUID outputAccountId;
    private UUID inputAccountId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private int version;
    private Instant createdAt;
    private Integer createdBy;
    private Instant updatedAt;
    private Integer updatedBy;
}
