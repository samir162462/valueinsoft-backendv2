package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAudit;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminDailyMetrics;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminReadModels;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformMetricsStatusResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewAlertItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewMetricsSnapshot;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Set;

@Service
public class PlatformAdminOverviewService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DbPlatformAdminAudit dbPlatformAdminAudit;
    private final DbPlatformAdminReadModels dbPlatformAdminReadModels;
    private final DbPlatformAdminDailyMetrics dbPlatformAdminDailyMetrics;
    private final PlatformAdminAlertService platformAdminAlertService;
    private final PlatformAdminMetricsService platformAdminMetricsService;
    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminOverviewService(DbPlatformAdminAudit dbPlatformAdminAudit,
                                        DbPlatformAdminReadModels dbPlatformAdminReadModels,
                                        DbPlatformAdminDailyMetrics dbPlatformAdminDailyMetrics,
                                        PlatformAdminAlertService platformAdminAlertService,
                                        PlatformAdminMetricsService platformAdminMetricsService,
                                        PlatformAuthorizationService platformAuthorizationService) {
        this.dbPlatformAdminAudit = dbPlatformAdminAudit;
        this.dbPlatformAdminReadModels = dbPlatformAdminReadModels;
        this.dbPlatformAdminDailyMetrics = dbPlatformAdminDailyMetrics;
        this.platformAdminAlertService = platformAdminAlertService;
        this.platformAdminMetricsService = platformAdminMetricsService;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    public PlatformOverviewResponse getOverviewForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.read");
        PlatformOverviewResponse overview = dbPlatformAdminReadModels.getOverview();
        PlatformOverviewMetricsSnapshot snapshot = dbPlatformAdminDailyMetrics.getLatestOverviewSnapshot();
        PlatformMetricsStatusResponse metricsStatus = platformAdminMetricsService.getMetricsStatusSnapshot();
        Set<String> acknowledgedAlertKeys = platformAdminAlertService.getActiveAcknowledgedAlertKeysSnapshot();

        if (snapshot == null) {
            overview.setMetricsSnapshotDate(null);
            overview.setMetricsTenantsRepresented(0);
            overview.setMetricsSalesAmount(BigDecimal.ZERO);
            overview.setMetricsExpenseAmount(BigDecimal.ZERO);
            overview.setMetricsCollectedAmount(BigDecimal.ZERO);
            overview.setMetricsNetAmount(BigDecimal.ZERO);
            overview.setMetricsStatus(metricsStatus);
            overview.setAlerts(filterAcknowledgedAlerts(buildAlerts(overview, metricsStatus), acknowledgedAlertKeys));
            overview.setRecentAdminActions(dbPlatformAdminAudit.getRecentAuditEvents(platformAdminAlertService.getRecentAdminActionsLimit()));
            return overview;
        }

        overview.setMetricsSnapshotDate(snapshot.getMetricDate());
        overview.setMetricsTenantsRepresented(snapshot.getTenantsRepresented());
        overview.setMetricsSalesAmount(defaultAmount(snapshot.getSalesAmount()));
        overview.setMetricsExpenseAmount(defaultAmount(snapshot.getExpenseAmount()));
        overview.setMetricsCollectedAmount(defaultAmount(snapshot.getCollectedAmount()));
        overview.setMetricsNetAmount(defaultAmount(snapshot.getNetAmount()));
        overview.setMetricsStatus(metricsStatus);
        overview.setAlerts(filterAcknowledgedAlerts(buildAlerts(overview, metricsStatus), acknowledgedAlertKeys));
        overview.setRecentAdminActions(dbPlatformAdminAudit.getRecentAuditEvents(platformAdminAlertService.getRecentAdminActionsLimit()));
        return overview;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private ArrayList<PlatformOverviewAlertItem> buildAlerts(PlatformOverviewResponse overview,
                                                             PlatformMetricsStatusResponse metricsStatus) {
        ArrayList<PlatformOverviewAlertItem> alerts = new ArrayList<>();

        if (overview.getSuspendedCompanies() > 0) {
            alerts.add(new PlatformOverviewAlertItem(
                    "suspended_companies",
                    "warning",
                    "Suspended companies",
                    "One or more tenant companies are suspended and require platform review.",
                    overview.getSuspendedCompanies()
            ));
        }

        if (overview.getLockedBranches() > 0) {
            alerts.add(new PlatformOverviewAlertItem(
                    "locked_branches",
                    "warning",
                    "Locked branches",
                    "Some tenant branches are locked and may be blocking branch operations.",
                    overview.getLockedBranches()
            ));
        }

        if (overview.getTenantsInOnboarding() > 0) {
            alerts.add(new PlatformOverviewAlertItem(
                    "tenants_in_onboarding",
                    "info",
                    "Onboarding in progress",
                    "There are tenants still in onboarding or recovery-required states.",
                    overview.getTenantsInOnboarding()
            ));
        }

        if (overview.getUnpaidSubscriptions() > 0) {
            alerts.add(new PlatformOverviewAlertItem(
                    "unpaid_subscriptions",
                    "warning",
                    "Unpaid subscriptions",
                    "Branches with unpaid latest subscription records need commercial follow-up.",
                    overview.getUnpaidSubscriptions()
            ));
        }

        if (metricsStatus != null && metricsStatus.isStale()) {
            alerts.add(new PlatformOverviewAlertItem(
                    "stale_metrics_snapshot",
                    "critical",
                    "Daily metrics snapshot is stale",
                    metricsStatus.getLatestSnapshotDate() == null
                            ? "No daily metrics snapshot is available yet."
                            : "The latest daily metrics snapshot is older than one day and needs refresh attention.",
                    metricsStatus.getSnapshotLagDays()
            ));
        }

        if (metricsStatus != null
                && metricsStatus.getLatestRefreshResultStatus() != null
                && !"success".equalsIgnoreCase(metricsStatus.getLatestRefreshResultStatus())) {
            alerts.add(new PlatformOverviewAlertItem(
                    "latest_metrics_refresh_failed",
                    "critical",
                    "Latest metrics refresh did not complete cleanly",
                    "The latest metrics refresh audit entry is not marked as success.",
                    metricsStatus.getLastFailedTenantIds() == null ? 0 : metricsStatus.getLastFailedTenantIds().size()
            ));
        }

        if (metricsStatus != null
                && metricsStatus.getLastFailedTenantIds() != null
                && !metricsStatus.getLastFailedTenantIds().isEmpty()) {
            alerts.add(new PlatformOverviewAlertItem(
                    "metrics_partial_refresh",
                    "warning",
                    "Metrics refresh had tenant failures",
                    "The latest metrics refresh skipped one or more tenant snapshots.",
                    metricsStatus.getLastFailedTenantIds().size()
            ));
        }

        if (overview.getMetricsNetAmount() != null && overview.getMetricsNetAmount().compareTo(ZERO) < 0) {
            alerts.add(new PlatformOverviewAlertItem(
                    "negative_operational_net",
                    "warning",
                    "Negative operational net snapshot",
                    "Latest snapshot sales minus expenses is negative across the represented tenants.",
                    null
            ));
        }

        if (overview.getActiveCompanies() > 0
                && overview.getMetricsTenantsRepresented() > 0
                && overview.getMetricsTenantsRepresented() < overview.getActiveCompanies()) {
            alerts.add(new PlatformOverviewAlertItem(
                    "metrics_snapshot_coverage_gap",
                    "warning",
                    "Metrics snapshot coverage gap",
                    "Latest daily snapshot does not yet cover all active tenant companies.",
                    overview.getActiveCompanies() - overview.getMetricsTenantsRepresented()
            ));
        }

        if (overview.getActiveSubscriptions() > 0
                && overview.getUnpaidSubscriptions() > 0) {
            BigDecimal unpaidRatio = BigDecimal.valueOf(overview.getUnpaidSubscriptions())
                    .divide(BigDecimal.valueOf(overview.getActiveSubscriptions()), 2, RoundingMode.HALF_UP);
            if (unpaidRatio.compareTo(platformAdminAlertService.getHighUnpaidSubscriptionRatioThreshold()) >= 0) {
                alerts.add(new PlatformOverviewAlertItem(
                        "high_unpaid_subscription_ratio",
                        "warning",
                        "High unpaid subscription ratio",
                        "Unpaid latest subscription records are high compared with currently active subscriptions.",
                        overview.getUnpaidSubscriptions()
                ));
            }
        }

        return alerts;
    }

    private ArrayList<PlatformOverviewAlertItem> filterAcknowledgedAlerts(ArrayList<PlatformOverviewAlertItem> alerts,
                                                                          Set<String> acknowledgedAlertKeys) {
        if (acknowledgedAlertKeys == null || acknowledgedAlertKeys.isEmpty()) {
            return alerts;
        }

        ArrayList<PlatformOverviewAlertItem> filteredAlerts = new ArrayList<>();
        for (PlatformOverviewAlertItem alert : alerts) {
            if (!acknowledgedAlertKeys.contains(alert.getAlertKey())) {
                filteredAlerts.add(alert);
            }
        }
        return filteredAlerts;
    }
}
