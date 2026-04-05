package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingPackageSummary {
    private String packageId;
    private String packageDisplayName;
    private int tenantCount;
    private int activeSubscriptions;
    private int unpaidSubscriptions;
    private BigDecimal collectedAmount;
    private BigDecimal outstandingAmount;
}
