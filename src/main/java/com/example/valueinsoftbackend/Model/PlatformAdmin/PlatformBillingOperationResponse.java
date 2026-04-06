package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingOperationResponse {
    private String operationType;
    private int processedItems;
    private int generatedItems;
    private int skippedItems;
    private Timestamp executedAt;
}
