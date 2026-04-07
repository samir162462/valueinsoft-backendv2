package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformPlanItem {
    private String packageId;
    private String displayName;
    private String status;
    private String priceCode;
    private String configVersion;
    private String description;
    private BigDecimal monthlyPriceAmount;
    private String currencyCode;
    private int displayOrder;
    private boolean featured;
    private Integer maxUsers;
    private ArrayList<PlatformPlanModuleItem> modules;
}
