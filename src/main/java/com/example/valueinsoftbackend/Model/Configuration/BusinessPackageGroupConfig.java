package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessPackageGroupConfig {
    private Long groupId;
    private String groupKey;
    private String displayName;
    private String status;
    private int displayOrder;
    private ArrayList<BusinessPackageCategoryConfig> categories;
}
