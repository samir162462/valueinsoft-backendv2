package com.example.valueinsoftbackend.Model.Request.Configuration;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class SaveRoleGrantRequest {

    @NotBlank
    @Size(max = 120)
    private String capabilityKey;

    @NotBlank
    @Size(max = 20)
    private String scopeType;

    @Size(max = 20)
    private String grantMode;

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

    public String getGrantMode() {
        return grantMode;
    }

    public void setGrantMode(String grantMode) {
        this.grantMode = grantMode;
    }
}
