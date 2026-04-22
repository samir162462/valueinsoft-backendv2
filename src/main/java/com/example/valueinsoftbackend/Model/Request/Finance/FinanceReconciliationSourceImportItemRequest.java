package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReconciliationSourceImportItemRequest {
    private String externalReference;
    private LocalDate sourceDate;
    private BigDecimal amount;
    private String currencyCode;
    private String description;
    private Map<String, Object> rawPayload;
}
