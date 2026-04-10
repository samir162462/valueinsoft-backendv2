package com.example.valueinsoftbackend.Model.PlatformAdmin;

import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTenantBusinessPackageAssignmentResponse {
    private int tenantId;
    private String businessPackageId;
    private String previousBusinessPackageId;
    private boolean reseededBranches;
    private ArrayList<Integer> reseededBranchIds;
    private BusinessPackageConfig businessPackage;
}
