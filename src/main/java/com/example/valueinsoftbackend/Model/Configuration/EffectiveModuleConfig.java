package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveModuleConfig {
    private String moduleId;
    private String displayName;
    private String category;
    private boolean enabled;
    private String source;
    private String mode;
}
