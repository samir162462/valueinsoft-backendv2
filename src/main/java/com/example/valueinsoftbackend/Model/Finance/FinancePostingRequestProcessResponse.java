package com.example.valueinsoftbackend.Model.Finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancePostingRequestProcessResponse {
    private int companyId;
    private UUID postingRequestId;
    private String sourceModule;
    private String sourceType;
    private String sourceId;
    private String status;
    private boolean processed;
    private UUID journalEntryId;
    private int attemptCount;
    private String message;
    private String correlationId;
}
