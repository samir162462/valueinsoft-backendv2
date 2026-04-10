package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessPackageCategoryConfig {
    private Long categoryId;
    private String categoryKey;
    private String displayName;
    private String status;
    private int displayOrder;
    private ArrayList<BusinessPackageSubcategoryConfig> subcategories;
}
