package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCompanyListItem {
    private int tenantId;
    private int companyId;
    private String companyName;
    private int ownerId;
    private String packageId;
    private String packageDisplayName;
    private String templateId;
    private String templateDisplayName;
    private String tenantStatus;
    private Timestamp createdAt;
    private int branchCount;
    private int userCount;
    private int unpaidBranchSubscriptions;
}
