package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessPackageSubcategoryConfig {
    private Long subcategoryId;
    private String subcategoryKey;
    private String displayName;
    private String status;
    private int displayOrder;
}
