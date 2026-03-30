package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackagePlanConfig {
    private String packageId;
    private String displayName;
    private String status;
    private String priceCode;
    private String configVersion;
    private String description;
}
