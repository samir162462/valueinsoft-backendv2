package com.example.valueinsoftbackend.Model.InventoryAudit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditAiInsight {
    private String title;
    private String detail;
    private String severity;
    private String metric;
}
