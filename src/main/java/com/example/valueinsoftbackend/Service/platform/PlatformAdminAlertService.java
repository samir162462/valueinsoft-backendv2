package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAlertAcknowledgments;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminOperations;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminReadModels;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAlertNotificationOutbox;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertAcknowledgmentItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertAcknowledgmentsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertNotificationOutboxPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAlertSettingsResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformAlertAcknowledgmentRequest;
import com.example.valueinsoftbackend.Model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class PlatformAdminAlertService {

    private static final Set<String> ACKNOWLEDGEABLE_ALERT_KEYS = Set.of(
            "suspended_companies",
            "locked_branches",
            "tenants_in_onboarding",
            "unpaid_subscriptions",
            "stale_metrics_snapshot",
            "latest_metrics_refresh_failed",
            "metrics_partial_refresh",
            "negative_operational_net",
            "metrics_snapshot_coverage_gap",
            "high_unpaid_subscription_ratio"
    );

    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbPlatformAdminAlertAcknowledgments dbPlatformAdminAlertAcknowledgments;
    private final DbPlatformAlertNotificationOutbox dbPlatformAlertNotificationOutbox;
    private final DbPlatformAdminReadModels dbPlatformAdminReadModels;
    private final DbPlatformAdminOperations dbPlatformAdminOperations;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public PlatformAdminAlertService(PlatformAuthorizationService platformAuthorizationService,
                                     DbPlatformAdminAlertAcknowledgments dbPlatformAdminAlertAcknowledgments,
                                     DbPlatformAlertNotificationOutbox dbPlatformAlertNotificationOutbox,
                                     DbPlatformAdminReadModels dbPlatformAdminReadModels,
                                     DbPlatformAdminOperations dbPlatformAdminOperations,
                                     ObjectMapper objectMapper,
                                     Environment environment) {
        this.platformAuthorizationService = platformAuthorizationService;
        this.dbPlatformAdminAlertAcknowledgments = dbPlatformAdminAlertAcknowledgments;
        this.dbPlatformAlertNotificationOutbox = dbPlatformAlertNotificationOutbox;
        this.dbPlatformAdminReadModels = dbPlatformAdminReadModels;
        this.dbPlatformAdminOperations = dbPlatformAdminOperations;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public PlatformAlertSettingsResponse getAlertSettingsForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.read");
        return getAlertSettingsSnapshot();
    }

    public PlatformAlertAcknowledgmentsPageResponse getAcknowledgmentHistoryForAuthenticatedUser(String authenticatedName,
                                                                                                 String alertKey,
                                                                                                 Integer tenantId,
                                                                                                 Integer branchId,
                                                                                                 Boolean activeOnly,
                                                                                                 int page,
                                                                                                 int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.read");
        AlertScope scope = resolveScope(tenantId, branchId);
        return dbPlatformAdminAlertAcknowledgments.getAcknowledgments(
                normalizeAlertKey(alertKey),
                scope.getTenantId(),
                scope.getBranchId(),
                activeOnly,
                page,
                size
        );
    }

    public PlatformAlertNotificationOutboxPageResponse getNotificationOutboxForAuthenticatedUser(String authenticatedName,
                                                                                                  String alertKey,
                                                                                                  String eventType,
                                                                                                  String status,
                                                                                                  Integer tenantId,
                                                                                                  Integer branchId,
                                                                                                  int page,
                                                                                                  int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.read");
        AlertScope scope = resolveScope(tenantId, branchId);
        return dbPlatformAlertNotificationOutbox.getOutboxEvents(
                normalizeAlertKey(alertKey),
                normalizeNullable(eventType),
                normalizeNullable(status),
                scope.getTenantId(),
                scope.getBranchId(),
                page,
                size
        );
    }

    @Transactional
    public PlatformAlertAcknowledgmentItem acknowledgeAlertForAuthenticatedUser(String authenticatedName,
                                                                                String alertKey,
                                                                                PlatformAlertAcknowledgmentRequest request) {
        User actor = platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        String normalizedAlertKey = normalizeAlertKey(alertKey);
        if (!ACKNOWLEDGEABLE_ALERT_KEYS.contains(normalizedAlertKey)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PLATFORM_ALERT_KEY_INVALID",
                    "Unsupported platform alert key: " + normalizedAlertKey
            );
        }
        AlertScope scope = resolveScope(
                request == null ? null : request.getTenantId(),
                request == null ? null : request.getBranchId()
        );

        LocalDateTime expiresAtValue = request == null ? null : request.getExpiresAt();
        if (expiresAtValue == null) {
            expiresAtValue = LocalDateTime.now().plusHours(getDefaultAcknowledgmentHours());
        }
        if (!expiresAtValue.isAfter(LocalDateTime.now())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PLATFORM_ALERT_ACK_EXPIRES_AT_INVALID",
                    "Alert acknowledgment expiry must be in the future"
            );
        }

        String note = normalizeNullable(request == null ? null : request.getNote());
        boolean notify = request != null && Boolean.TRUE.equals(request.getNotify());
        PlatformAlertAcknowledgmentItem item = dbPlatformAdminAlertAcknowledgments.createAcknowledgment(
                normalizedAlertKey,
                scope.getTenantId(),
                scope.getBranchId(),
                note,
                actor.getUserId(),
                actor.getUserName(),
                Timestamp.valueOf(expiresAtValue)
        );
        Long notificationId = notify
                ? dbPlatformAlertNotificationOutbox.createOutboxEvent(
                normalizedAlertKey,
                scope.getTenantId(),
                scope.getBranchId(),
                "acknowledged",
                toJson(buildMap(
                        "acknowledgmentId", item.getAcknowledgmentId(),
                        "alertKey", normalizedAlertKey,
                        "tenantId", scope.getTenantId(),
                        "branchId", scope.getBranchId(),
                        "note", note,
                        "expiresAt", item.getExpiresAt()
                )),
                actor.getUserId(),
                actor.getUserName()
        )
                : null;

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.admin.write",
                "platform.alert.acknowledge",
                scope.getTenantId(),
                scope.getBranchId(),
                toJson(buildMap(
                        "alertKey", normalizedAlertKey,
                        "note", note,
                        "expiresAt", item.getExpiresAt(),
                        "notify", notify
                )),
                toJson(buildMap(
                        "acknowledgmentId", item.getAcknowledgmentId(),
                        "notificationId", notificationId
                )),
                "success",
                null
        );

        return item;
    }

    @Transactional
    public PlatformAlertAcknowledgmentItem clearAcknowledgmentForAuthenticatedUser(String authenticatedName,
                                                                                   String alertKey,
                                                                                   Integer tenantId,
                                                                                   Integer branchId,
                                                                                   boolean notify) {
        User actor = platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.admin.write");
        String normalizedAlertKey = normalizeAlertKey(alertKey);
        if (!ACKNOWLEDGEABLE_ALERT_KEYS.contains(normalizedAlertKey)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PLATFORM_ALERT_KEY_INVALID",
                    "Unsupported platform alert key: " + normalizedAlertKey
            );
        }
        AlertScope scope = resolveScope(tenantId, branchId);

        PlatformAlertAcknowledgmentItem item = dbPlatformAdminAlertAcknowledgments.clearLatestActiveAcknowledgment(
                normalizedAlertKey,
                scope.getTenantId(),
                scope.getBranchId(),
                actor.getUserId(),
                actor.getUserName()
        );
        Long notificationId = notify
                ? dbPlatformAlertNotificationOutbox.createOutboxEvent(
                normalizedAlertKey,
                scope.getTenantId(),
                scope.getBranchId(),
                "cleared",
                toJson(buildMap(
                        "acknowledgmentId", item.getAcknowledgmentId(),
                        "alertKey", normalizedAlertKey,
                        "tenantId", scope.getTenantId(),
                        "branchId", scope.getBranchId(),
                        "clearedAt", item.getClearedAt()
                )),
                actor.getUserId(),
                actor.getUserName()
        )
                : null;

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.admin.write",
                "platform.alert.acknowledgment.clear",
                scope.getTenantId(),
                scope.getBranchId(),
                toJson(buildMap(
                        "alertKey", normalizedAlertKey,
                        "notify", notify
                )),
                toJson(buildMap(
                        "acknowledgmentId", item.getAcknowledgmentId(),
                        "clearedAt", item.getClearedAt(),
                        "notificationId", notificationId
                )),
                "success",
                null
        );

        return item;
    }

    Set<String> getActiveAcknowledgedAlertKeysSnapshot() {
        return dbPlatformAdminAlertAcknowledgments.getActiveAcknowledgedAlertKeysForGlobalScope();
    }

    int getStaleMetricsAfterDaysThreshold() {
        return Math.max(1, environment.getProperty("platform.admin.alerts.stale-metrics-after-days", Integer.class, 1));
    }

    BigDecimal getHighUnpaidSubscriptionRatioThreshold() {
        return new BigDecimal(environment.getProperty(
                "platform.admin.alerts.high-unpaid-subscription-ratio",
                "0.50"
        ));
    }

    int getRecentAdminActionsLimit() {
        return Math.min(
                Math.max(environment.getProperty("platform.admin.overview.recent-actions.limit", Integer.class, 5), 1),
                20
        );
    }

    PlatformAlertSettingsResponse getAlertSettingsSnapshot() {
        return new PlatformAlertSettingsResponse(
                getStaleMetricsAfterDaysThreshold(),
                getHighUnpaidSubscriptionRatioThreshold(),
                getDefaultAcknowledgmentHours(),
                getRecentAdminActionsLimit(),
                dbPlatformAdminAlertAcknowledgments.getActiveAcknowledgments(),
                new Timestamp(System.currentTimeMillis())
        );
    }

    private int getDefaultAcknowledgmentHours() {
        return Math.max(1, environment.getProperty("platform.admin.alerts.ack.default-hours", Integer.class, 12));
    }

    private String normalizeAlertKey(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "" : normalized.toLowerCase();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
                    "PLATFORM_ALERT_SERIALIZATION_FAILED",
                    "Could not serialize platform alert audit payload"
            );
        }
    }

    private AlertScope resolveScope(Integer tenantId, Integer branchId) {
        Integer normalizedTenantId = tenantId;
        Integer normalizedBranchId = branchId;

        if (normalizedTenantId != null && !dbPlatformAdminReadModels.tenantExists(normalizedTenantId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TENANT_NOT_FOUND",
                    "Tenant not found"
            );
        }

        if (normalizedBranchId != null) {
            Integer branchTenantId = dbPlatformAdminReadModels.getBranchTenantId(normalizedBranchId);
            if (branchTenantId == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BRANCH_NOT_FOUND",
                        "Branch not found"
                );
            }
            if (normalizedTenantId != null && !normalizedTenantId.equals(branchTenantId)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "PLATFORM_ALERT_SCOPE_MISMATCH",
                        "Branch does not belong to the provided tenant scope"
                );
            }
        }

        return new AlertScope(normalizedTenantId, normalizedBranchId);
    }

    private static class AlertScope {
        private final Integer tenantId;
        private final Integer branchId;

        private AlertScope(Integer tenantId, Integer branchId) {
            this.tenantId = tenantId;
            this.branchId = branchId;
        }

        private Integer getTenantId() {
            return tenantId;
        }

        private Integer getBranchId() {
            return branchId;
        }
    }
}
