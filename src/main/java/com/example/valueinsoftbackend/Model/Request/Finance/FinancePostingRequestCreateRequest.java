package com.example.valueinsoftbackend.Model.Request.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancePostingRequestCreateRequest {
    private int companyId;
    private Integer branchId;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private LocalDate postingDate;
    private UUID fiscalPeriodId;
    private Map<String, Object> requestPayload;
}
