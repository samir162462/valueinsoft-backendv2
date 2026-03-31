package com.example.valueinsoftbackend.Model.Request.Configuration;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class SaveUserGrantOverrideRequest {

    @NotBlank
    @Size(max = 120)
    private String capabilityKey;

    @NotBlank
    @Size(max = 20)
    private String scopeType;

    @Positive
    private Integer scopeBranchId;

    @NotBlank
    @Size(max = 20)
    private String grantMode;

    @Size(max = 80)
    private String reason;

    public String getCapabilityKey() {
        return capabilityKey;
    }

    public void setCapabilityKey(String capabilityKey) {
        this.capabilityKey = capabilityKey;
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

    public String getGrantMode() {
        return grantMode;
    }

    public void setGrantMode(String grantMode) {
        this.grantMode = grantMode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
