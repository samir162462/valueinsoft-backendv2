package com.example.valueinsoftbackend.Model.Request.PlatformAdmin;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;

@Data
public class BusinessPackageCategoryRequest {
    @NotBlank(message = "categoryKey is required")
    private String categoryKey;

    @NotBlank(message = "displayName is required")
    private String displayName;

    private String status = "active";

    @Min(value = 0, message = "displayOrder must be zero or greater")
    private int displayOrder;

    @Valid
    private ArrayList<BusinessPackageSubcategoryRequest> subcategories = new ArrayList<>();
}
