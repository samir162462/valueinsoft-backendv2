package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOverviewPackageSummary {
    private String packageId;
    private String packageDisplayName;
    private int tenantCount;
}
