package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyTemplateModuleDefault {
    private String templateId;
    private String moduleId;
    private boolean enabled;
    private boolean recommended;
    private String mode;
    private String notes;
}
