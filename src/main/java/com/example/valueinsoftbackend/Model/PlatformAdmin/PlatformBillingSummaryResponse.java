package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBillingSummaryResponse {
    private String packageFilter;
    private int activeSubscriptions;
    private int unpaidSubscriptions;
    private int expiredPaidSubscriptions;
    private int tenantsWithUnpaidSubscriptions;
    private int tenantsRepresented;
    private BigDecimal collectedAmount;
    private BigDecimal outstandingAmount;
    private ArrayList<PlatformBillingPackageSummary> packageBreakdown;
    private Timestamp generatedAt;
}
