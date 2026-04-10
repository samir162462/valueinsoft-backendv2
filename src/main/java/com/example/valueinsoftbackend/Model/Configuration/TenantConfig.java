package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfig {
    private int tenantId;
    private String packageId;
    private String templateId;
    private String businessPackageId;
    private String status;
    private String configVersion;
    private String legacyPlanName;
    private String bootstrapSource;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
