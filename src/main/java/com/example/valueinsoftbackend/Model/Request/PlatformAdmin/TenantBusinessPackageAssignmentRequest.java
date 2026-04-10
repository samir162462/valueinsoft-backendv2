package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TenantBusinessPackageAssignmentRequest {
    @NotBlank(message = "businessPackageId is required")
    private String businessPackageId;

    private boolean reseedBranches;
}
