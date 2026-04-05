package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCompany360Response {
    private int tenantId;
    private int companyId;
    private String companyName;
    private int ownerId;
    private String tenantStatus;
    private String packageId;
    private String packageDisplayName;
    private String templateId;
    private String templateDisplayName;
    private String onboardingStatus;
    private Timestamp createdAt;
    private int branchCount;
    private int activeBranchCount;
    private int lockedBranchCount;
    private int userCount;
    private int unpaidBranchSubscriptions;
    private ArrayList<PlatformCompanyBranchSummary> branches;
}
