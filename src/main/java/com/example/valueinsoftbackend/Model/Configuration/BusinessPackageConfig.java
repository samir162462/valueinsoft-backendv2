package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessPackageConfig {
    private String packageId;
    private String displayName;
    private String onboardingLabel;
    private String businessType;
    private String status;
    private String configVersion;
    private String description;
    private String defaultTemplateId;
    private int displayOrder;
    private boolean featured;
    private ArrayList<BusinessPackageGroupConfig> groups;
}
