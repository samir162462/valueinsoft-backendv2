package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
public class BusinessPackageSubcategoryRequest {
    @NotBlank(message = "subcategoryKey is required")
    private String subcategoryKey;

    @NotBlank(message = "displayName is required")
    private String displayName;

    private String status = "active";

    @Min(value = 0, message = "displayOrder must be zero or greater")
    private int displayOrder;
}
