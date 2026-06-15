package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAudit;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminDailyMetrics;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminOperations;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Company;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformMetricsRefreshResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformMetricsStatusResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewMetricsSnapshot;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformRevenueTrendResponse;
import com.example.valueinsoftbackend.Model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformAdminMetricsService {

    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbPlatformAdminAudit dbPlatformAdminAudit;
    private final DbPlatformAdminDailyMetrics dbPlatformAdminDailyMetrics;
    private final DbPlatformAdminOperations dbPlatformAdminOperations;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public PlatformAdminMetricsService(PlatformAuthorizationService platformAuthorizationService,
                                       DbPlatformAdminAudit dbPlatformAdminAudit,
                                       DbPlatformAdminDailyMetrics dbPlatformAdminDailyMetrics,
                                       DbPlatformAdminOperations dbPlatformAdminOperations,
                                       DbCompany dbCompany,
                                       DbBranch dbBranch,
                                       ObjectMapper objectMapper,
                                       Environment environment) {
        this.platformAuthorizationService = platformAuthorizationService;
        this.dbPlatformAdminAudit = dbPlatformAdminAudit;
        this.dbPlatformAdminDailyMetrics = dbPlatformAdminDailyMetrics;
        this.dbPlatformAdminOperations = dbPlatformAdminOperations;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public PlatformRevenueTrendResponse getRevenueTrendForAuthenticatedUser(String authenticatedName,
                                                                            int days,
                                                                            Integer tenantId,
                                                                            String packageId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        if (tenantId != null) {
            requireCompany(tenantId);
        }
        return dbPlatformAdminDailyMetrics.getRevenueTrend(days, tenantId, packageId);
    }

    @Transactional
    public PlatformMetricsRefreshResponse refreshDailyMetricsForAuthenticatedUser(String authenticatedName,
                                                                                  LocalDate metricDate,
                                                                                  Integer tenantId) {
        User actor = platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        return refreshDailyMetricsInternal(
                metricDate,
                tenantId,
                actor.getUserId(),
                actor.getUserName(),
                "platform.admin.write",
                "platform.metrics.refresh_daily"
        );
    }

    @Transactional
    public PlatformMetricsRefreshResponse refreshDailyMetricsForSystemSchedule(LocalDate metricDate) {
        return refreshDailyMetricsInternal(
                metricDate,
                null,
                null,
                "system",
                null,
                "platform.metrics.refresh_daily.scheduled"
        );
    }

    public PlatformMetricsStatusResponse getMetricsStatusForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.read");
        return getMetricsStatusSnapshot();
    }

    private int refreshTenantMetrics(int tenantId, LocalDate metricDate) {
        Company company = requireCompany(tenantId);
        List<Branch> branches = dbBranch.getBranchByCompanyId(tenantId);

        int branchCount = branches.size();
        int activeBranchCount = 0;
        int lockedBranchCount = 0;
        int productCount = 0;
        BigDecimal salesAmount = BigDecimal.ZERO;
        int processedBranches = 0;

        for (Branch branch : branches) {
            String branchStatus = dbPlatformAdminDailyMetrics.getBranchStatus(branch.getBranchID());
            if ("locked".equalsIgnoreCase(branchStatus)) {
                lockedBranchCount++;
            } else if ("active".equalsIgnoreCase(branchStatus)) {
                activeBranchCount++;
            }

            int branchProductCount = dbPlatformAdminDailyMetrics.getBranchProductCount(company.getCompanyId(), branch.getBranchID());
            int branchClientCount = dbPlatformAdminDailyMetrics.getBranchClientCount(company.getCompanyId(), branch.getBranchID());
            int activeUsersCount = dbPlatformAdminDailyMetrics.getBranchActiveUsersCount(company.getCompanyId(), branch.getBranchID(), metricDate);
            int shiftCount = dbPlatformAdminDailyMetrics.getBranchShiftCount(company.getCompanyId(), branch.getBranchID(), metricDate);
            int salesCount = dbPlatformAdminDailyMetrics.getBranchSalesCount(company.getCompanyId(), branch.getBranchID(), metricDate);
            BigDecimal branchSalesAmount = dbPlatformAdminDailyMetrics.getBranchSalesAmount(company.getCompanyId(), branch.getBranchID(), metricDate);
            int inventoryAdjustmentCount = dbPlatformAdminDailyMetrics.getBranchInventoryAdjustmentCount(company.getCompanyId(), branch.getBranchID(), metricDate);

            dbPlatformAdminDailyMetrics.upsertBranchDailyMetric(
                    metricDate,
                    tenantId,
                    branch.getBranchID(),
                    branchStatus,
                    activeUsersCount,
                    branchClientCount,
                    branchProductCount,
                    shiftCount,
                    salesCount,
                    branchSalesAmount,
                    inventoryAdjustmentCount
            );

            productCount += branchProductCount;
            salesAmount = salesAmount.add(branchSalesAmount);
            processedBranches++;
        }

        int userCount = dbPlatformAdminDailyMetrics.getTenantUserCount(tenantId);
        int clientCount = dbPlatformAdminDailyMetrics.getTenantClientCount(company.getCompanyId());
        int unpaidBranchSubscriptions = dbPlatformAdminDailyMetrics.getTenantUnpaidBranchSubscriptions(tenantId);
        BigDecimal collectedAmount = dbPlatformAdminDailyMetrics.getCompanyCollectedAmountForDate(company.getCompanyId(), metricDate);
        BigDecimal expenseAmount = dbPlatformAdminDailyMetrics.getCompanyExpenseAmountForDate(company.getCompanyId(), metricDate);

        dbPlatformAdminDailyMetrics.upsertTenantDailyMetric(
                metricDate,
                tenantId,
                branchCount,
                userCount,
                clientCount,
                productCount,
                activeBranchCount,
                lockedBranchCount,
                unpaidBranchSubscriptions,
                collectedAmount,
                salesAmount,
                expenseAmount
        );

        return processedBranches;
    }

    private PlatformMetricsRefreshResponse refreshDailyMetricsInternal(LocalDate metricDate,
                                                                       Integer tenantId,
                                                                       Integer actorUserId,
                                                                       String actorUserName,
                                                                       String capabilityKey,
                                                                       String actionType) {
        LocalDate targetDate = metricDate == null ? LocalDate.now() : metricDate;

        ArrayList<Integer> targetTenantIds = dbPlatformAdminDailyMetrics.getTenantIds(tenantId);
        ArrayList<Integer> failedTenantIds = new ArrayList<>();
        int processedTenants = 0;
        int processedBranches = 0;

        for (Integer targetTenantId : targetTenantIds) {
            try {
                processedBranches += refreshTenantMetrics(targetTenantId, targetDate);
                processedTenants++;
            } catch (Exception exception) {
                failedTenantIds.add(targetTenantId);
                if (tenantId != null) {
                    throw exception;
                }
            }
        }

        Timestamp refreshedAt = new Timestamp(System.currentTimeMillis());
        dbPlatformAdminOperations.insertAuditLog(
                actorUserId,
                actorUserName,
                capabilityKey,
                actionType,
                tenantId,
                null,
                toJson(buildMap(
                        "metricDate", Date.valueOf(targetDate),
                        "tenantFilter", tenantId
                )),
                toJson(buildMap(
                        "processedTenants", processedTenants,
                        "processedBranches", processedBranches,
                        "failedTenantIds", failedTenantIds
                )),
                failedTenantIds.isEmpty() ? "success" : "rejected",
                null
        );

        return new PlatformMetricsRefreshResponse(
                Date.valueOf(targetDate),
                tenantId,
                processedTenants,
                processedBranches,
                failedTenantIds,
                actorUserName,
                refreshedAt
        );
    }

    PlatformMetricsStatusResponse getMetricsStatusSnapshot() {
        PlatformAuditEventItem latestRefreshEvent = dbPlatformAdminAudit.getLatestMetricsRefreshEvent();
        PlatformAuditEventItem latestSuccessfulRefreshEvent = dbPlatformAdminAudit.getLatestSuccessfulMetricsRefreshEvent();
        PlatformOverviewMetricsSnapshot latestSnapshot = dbPlatformAdminDailyMetrics.getLatestOverviewSnapshot();

        ArrayList<Integer> failedTenantIds = new ArrayList<>();
        Integer processedTenants = null;
        Integer processedBranches = null;

        if (latestRefreshEvent != null && latestRefreshEvent.getContextSummaryJson() != null) {
            try {
                JsonNode contextNode = objectMapper.readTree(latestRefreshEvent.getContextSummaryJson());
                if (contextNode.has("processedTenants") && contextNode.get("processedTenants").isNumber()) {
                    processedTenants = contextNode.get("processedTenants").intValue();
                }
                if (contextNode.has("processedBranches") && contextNode.get("processedBranches").isNumber()) {
                    processedBranches = contextNode.get("processedBranches").intValue();
                }
                JsonNode failedTenantIdsNode = contextNode.get("failedTenantIds");
                if (failedTenantIdsNode != null && failedTenantIdsNode.isArray()) {
                    for (JsonNode failedTenantIdNode : failedTenantIdsNode) {
                        if (failedTenantIdNode.isInt()) {
                            failedTenantIds.add(failedTenantIdNode.intValue());
                        }
                    }
                }
            } catch (JsonProcessingException ignored) {
                failedTenantIds.clear();
            }
        }

        Date latestSnapshotDate = latestSnapshot == null ? null : latestSnapshot.getMetricDate();
        Integer snapshotLagDays = latestSnapshotDate == null
                ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(latestSnapshotDate.toLocalDate(), LocalDate.now()));
        int staleThresholdDays = Math.max(
                1,
                environment.getProperty("platform.admin.alerts.stale-metrics-after-days", Integer.class, 1)
        );
        boolean stale = latestSnapshotDate == null || (snapshotLagDays != null && snapshotLagDays > staleThresholdDays);

        return new PlatformMetricsStatusResponse(
                environment.getProperty("platform.admin.metrics.scheduler.enabled", Boolean.class, true),
                environment.getProperty("platform.admin.metrics.scheduler.cron", "0 30 2 * * *"),
                environment.getProperty("platform.admin.metrics.scheduler.zone", "Africa/Cairo"),
                latestRefreshEvent == null ? null : latestRefreshEvent.getCreatedAt(),
                latestRefreshEvent == null ? null : latestRefreshEvent.getResultStatus(),
                latestRefreshEvent == null ? null : latestRefreshEvent.getActorUserName(),
                latestRefreshEvent == null ? null : latestRefreshEvent.getActionType(),
                latestSuccessfulRefreshEvent == null ? null : latestSuccessfulRefreshEvent.getCreatedAt(),
                latestSnapshotDate,
                latestSnapshot == null ? 0 : latestSnapshot.getTenantsRepresented(),
                snapshotLagDays,
                stale,
                processedTenants,
                processedBranches,
                failedTenantIds
        );
    }

    private Company requireCompany(int tenantId) {
        Company company = dbCompany.getCompanyById(tenantId);
        if (company == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }
        return company;
    }

    private Map<String, Object> buildMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PLATFORM_METRICS_SERIALIZATION_FAILED",
                    "Could not serialize platform metrics audit payload"
            );
        }
    }
}
