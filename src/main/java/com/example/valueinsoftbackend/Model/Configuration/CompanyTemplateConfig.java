package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyTemplateConfig {
    private String templateId;
    private String displayName;
    private String businessType;
    private String status;
    private String configVersion;
    private String description;
}
