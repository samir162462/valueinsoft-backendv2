package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformMetricsRefreshResponse {
    private Date metricDate;
    private Integer tenantFilter;
    private int processedTenants;
    private int processedBranches;
    private ArrayList<Integer> failedTenantIds;
    private String refreshedBy;
    private Timestamp refreshedAt;
}
