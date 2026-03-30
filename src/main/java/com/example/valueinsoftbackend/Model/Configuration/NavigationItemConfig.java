package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one enabled navigation item derived from the effective module configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NavigationItemConfig {
    private String moduleId;
    private String displayName;
    private String category;
    private String mode;
    private String source;
}
