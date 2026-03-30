package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformModuleConfig {
    private String moduleId;
    private String displayName;
    private String category;
    private String status;
    private boolean defaultEnabled;
    private String configVersion;
    private String description;
}
