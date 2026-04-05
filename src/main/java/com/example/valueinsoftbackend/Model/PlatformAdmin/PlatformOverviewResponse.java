package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOverviewResponse {
    private int totalCompanies;
    private int activeCompanies;
    private int suspendedCompanies;
    private int totalBranches;
    private int activeBranches;
    private int lockedBranches;
    private int tenantsInOnboarding;
    private int unpaidSubscriptions;
    private int activeSubscriptions;
    private ArrayList<PlatformOverviewPackageSummary> planDistribution;
    private Date metricsSnapshotDate;
    private int metricsTenantsRepresented;
    private BigDecimal metricsSalesAmount;
    private BigDecimal metricsExpenseAmount;
    private BigDecimal metricsCollectedAmount;
    private BigDecimal metricsNetAmount;
    private PlatformMetricsStatusResponse metricsStatus;
    private ArrayList<PlatformOverviewAlertItem> alerts;
    private ArrayList<PlatformAuditEventItem> recentAdminActions;
    private Timestamp generatedAt;
}
