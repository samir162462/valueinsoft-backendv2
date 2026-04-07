package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PlatformPlanModuleUpdateRequest {
    @NotBlank
    private String moduleId;

    private boolean enabled;
    private String mode;
    private String limitsJson;
    private String policyVersion;
}
