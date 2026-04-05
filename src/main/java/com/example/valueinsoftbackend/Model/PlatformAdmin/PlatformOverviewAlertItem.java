package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOverviewAlertItem {
    private String alertKey;
    private String severity;
    private String title;
    private String message;
    private Integer count;
}
