package com.example.valueinsoftbackend.Model.Request.Configuration;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class UpdateTenantModuleOverrideRequest {

    @NotNull
    private Boolean enabled;

    @Size(max = 40)
    private String mode;

    @Size(max = 80)
    private String reason;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
