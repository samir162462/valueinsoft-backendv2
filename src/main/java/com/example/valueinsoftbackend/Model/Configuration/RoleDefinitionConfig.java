package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDefinitionConfig {
    private String roleId;
    private String displayName;
    private String roleType;
    private String status;
    private String description;
}
