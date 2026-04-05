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
public class PlatformAlertSettingsResponse {
    private int staleMetricsAfterDays;
    private BigDecimal highUnpaidSubscriptionRatio;
    private int defaultAcknowledgmentHours;
    private int recentAdminActionsLimit;
    private ArrayList<PlatformAlertAcknowledgmentItem> activeAcknowledgments;
    private Timestamp generatedAt;
}
