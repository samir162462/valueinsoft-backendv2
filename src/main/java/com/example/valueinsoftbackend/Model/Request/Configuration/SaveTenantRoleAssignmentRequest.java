package com.example.valueinsoftbackend.Model.Request.Configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class SaveTenantRoleAssignmentRequest {

    @NotNull
    @Positive
    private Integer userId;

    @NotBlank
    @Size(max = 50)
    private String roleId;

    @NotBlank
    @Size(max = 20)
    private String scopeType;

    @Positive
    private Integer scopeBranchId;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Integer getScopeBranchId() {
        return scopeBranchId;
    }

    public void setScopeBranchId(Integer scopeBranchId) {
        this.scopeBranchId = scopeBranchId;
    }

    @AssertTrue(message = "branch scope requires scopeBranchId")
    public boolean isBranchScopeValid() {
        if ("branch".equalsIgnoreCase(scopeType)) {
            return scopeBranchId != null && scopeBranchId > 0;
        }
        return true;
    }
}
