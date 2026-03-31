package com.example.valueinsoftbackend.Model.Request.Configuration;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UpdateTenantWorkflowOverrideRequest {

    @NotBlank
    @Size(max = 60)
    private String flagValueJson;

    @Size(max = 80)
    private String reason;

    public String getFlagValueJson() {
        return flagValueJson;
    }

    public void setFlagValueJson(String flagValueJson) {
        this.flagValueJson = flagValueJson;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
