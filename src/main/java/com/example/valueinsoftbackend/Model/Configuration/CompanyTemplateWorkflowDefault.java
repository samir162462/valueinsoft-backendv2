package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyTemplateWorkflowDefault {
    private String templateId;
    private String flagKey;
    private String flagValueJson;
    private String notes;
}
