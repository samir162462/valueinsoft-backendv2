package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformCapabilityConfig {
    private String capabilityKey;
    private String moduleId;
    private String resource;
    private String action;
    private String scopeType;
    private String status;
    private String description;
}
