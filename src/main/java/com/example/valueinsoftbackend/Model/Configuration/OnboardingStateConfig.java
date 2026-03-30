package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStateConfig {
    private int tenantId;
    private String status;
    private String currentStep;
    private String completedStepsJson;
    private String requiredNextAction;
    private String diagnosticsJson;
}
