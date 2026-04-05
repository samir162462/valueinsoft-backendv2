package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCompanyBranchSummary {
    private int branchId;
    private int tenantId;
    private String branchName;
    private String branchLocation;
    private Timestamp branchEstablishedTime;
    private String runtimeStatus;
    private int userCount;
    private String latestSubscriptionStatus;
}
