package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;

@Data
public class BusinessPackageUpdateRequest {
    @NotBlank(message = "displayName is required")
    private String displayName;

    private String onboardingLabel;

    @NotBlank(message = "businessType is required")
    private String businessType;

    @NotBlank(message = "status is required")
    private String status = "active";

    @NotBlank(message = "configVersion is required")
    private String configVersion = "v1";

    @NotBlank(message = "description is required")
    private String description;

    @NotBlank(message = "defaultTemplateId is required")
    private String defaultTemplateId;

    @Min(value = 0, message = "displayOrder must be zero or greater")
    private int displayOrder;

    private boolean featured;

    @Valid
    private ArrayList<BusinessPackageGroupRequest> groups = new ArrayList<>();
}
