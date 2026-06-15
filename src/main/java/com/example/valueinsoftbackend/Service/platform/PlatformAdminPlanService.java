package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPackagePlans;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformModules;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.PackageModulePolicy;
import com.example.valueinsoftbackend.Model.Configuration.PackagePlanConfig;
import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformPlanItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformPlanModuleItem;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformPlanModuleUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.PlatformPlanUpdateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformAdminPlanService {

    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbPackagePlans dbPackagePlans;
    private final DbPlatformModules dbPlatformModules;
    private final ObjectMapper objectMapper;

    public PlatformAdminPlanService(PlatformAuthorizationService platformAuthorizationService,
                                    DbPackagePlans dbPackagePlans,
                                    DbPlatformModules dbPlatformModules,
                                    ObjectMapper objectMapper) {
        this.platformAuthorizationService = platformAuthorizationService;
        this.dbPackagePlans = dbPackagePlans;
        this.dbPlatformModules = dbPlatformModules;
        this.objectMapper = objectMapper;
    }

    public ArrayList<PlatformPlanItem> getPublicActivePlans() {
        return buildPlanItems(dbPackagePlans.getAllPackagePlans(true));
    }

    public ArrayList<PlatformPlanItem> getPlansForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        return buildPlanItems(dbPackagePlans.getAllPackagePlans(false));
    }

    @Transactional
    public PlatformPlanItem updatePlanForAuthenticatedUser(String authenticatedName,
                                                           String packageId,
                                                           PlatformPlanUpdateRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.write");
        PackagePlanConfig existing = dbPackagePlans.getPackagePlan(packageId);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PACKAGE_PLAN_NOT_FOUND", "Package plan not found");
        }

        PackagePlanConfig next = new PackagePlanConfig(
                existing.getPackageId(),
                normalizeRequired(request.getDisplayName(), "displayName"),
                normalizeStatus(request.getStatus()),
                normalizeRequired(request.getPriceCode(), "priceCode"),
                normalizeDefault(request.getConfigVersion(), existing.getConfigVersion()),
                normalizeRequired(request.getDescription(), "description"),
                request.getMonthlyPriceAmount() == null ? BigDecimal.ZERO : request.getMonthlyPriceAmount(),
                normalizeCurrency(request.getCurrencyCode()),
                request.getDisplayOrder(),
                request.isFeatured()
        );
        dbPackagePlans.updatePackagePlan(next);

        Map<String, PlatformPlanModuleUpdateRequest> moduleUpdates = new LinkedHashMap<>();
        ArrayList<PlatformPlanModuleUpdateRequest> requestedModules = request.getModules() == null
                ? new ArrayList<>()
                : request.getModules();
        for (PlatformPlanModuleUpdateRequest module : requestedModules) {
            moduleUpdates.put(module.getModuleId(), module);
        }

        if (request.getMaxUsers() != null) {
            PlatformPlanModuleUpdateRequest usersModule = moduleUpdates.computeIfAbsent("users", moduleId -> {
                PlatformPlanModuleUpdateRequest generated = new PlatformPlanModuleUpdateRequest();
                generated.setModuleId(moduleId);
                generated.setEnabled(true);
                generated.setMode("advanced");
                generated.setPolicyVersion(next.getConfigVersion());
                return generated;
            });
            usersModule.setLimitsJson(withMaxUsers(usersModule.getLimitsJson(), request.getMaxUsers()));
        }

        for (PlatformPlanModuleUpdateRequest module : moduleUpdates.values()) {
            dbPackagePlans.upsertPackageModulePolicy(
                    existing.getPackageId(),
                    module.getModuleId(),
                    module.isEnabled(),
                    module.getMode(),
                    normalizeLimitsJson(module.getLimitsJson()),
                    normalizeDefault(module.getPolicyVersion(), next.getConfigVersion())
            );
        }

        return buildPlanItem(dbPackagePlans.getPackagePlan(existing.getPackageId()));
    }

    private ArrayList<PlatformPlanItem> buildPlanItems(List<PackagePlanConfig> plans) {
        ArrayList<PlatformPlanItem> items = new ArrayList<>();
        for (PackagePlanConfig plan : plans) {
            items.add(buildPlanItem(plan));
        }
        return items;
    }

    private PlatformPlanItem buildPlanItem(PackagePlanConfig plan) {
        List<PlatformModuleConfig> modules = dbPlatformModules.getAllModules();
        Map<String, PackageModulePolicy> policies = new LinkedHashMap<>();
        for (PackageModulePolicy policy : dbPackagePlans.getPackageModulePolicies(plan.getPackageId())) {
            policies.put(policy.getModuleId(), policy);
        }

        ArrayList<PlatformPlanModuleItem> moduleItems = new ArrayList<>();
        Integer maxUsers = null;
        for (PlatformModuleConfig module : modules) {
            PackageModulePolicy policy = policies.get(module.getModuleId());
            String limitsJson = policy == null ? "{}" : normalizeLimitsJson(policy.getLimitsJson());
            if ("users".equals(module.getModuleId())) {
                maxUsers = extractMaxUsers(limitsJson);
            }
            moduleItems.add(new PlatformPlanModuleItem(
                    plan.getPackageId(),
                    module.getModuleId(),
                    module.getDisplayName(),
                    module.getCategory(),
                    policy != null && policy.isEnabled(),
                    policy == null ? null : policy.getMode(),
                    limitsJson,
                    policy == null ? plan.getConfigVersion() : policy.getPolicyVersion()
            ));
        }

        return new PlatformPlanItem(
                plan.getPackageId(),
                plan.getDisplayName(),
                plan.getStatus(),
                plan.getPriceCode(),
                plan.getConfigVersion(),
                plan.getDescription(),
                plan.getMonthlyPriceAmount(),
                plan.getCurrencyCode(),
                plan.getDisplayOrder(),
                plan.isFeatured(),
                maxUsers,
                moduleItems
        );
    }

    private Integer extractMaxUsers(String limitsJson) {
        try {
            JsonNode value = objectMapper.readTree(normalizeLimitsJson(limitsJson)).get("max_users");
            return value == null || !value.canConvertToInt() ? null : value.asInt();
        } catch (Exception exception) {
            return null;
        }
    }

    private String withMaxUsers(String limitsJson, int maxUsers) {
        if (maxUsers < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_MAX_USERS_INVALID", "maxUsers must be zero or greater");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> limits = objectMapper.readValue(normalizeLimitsJson(limitsJson), Map.class);
            limits.put("max_users", maxUsers);
            return objectMapper.writeValueAsString(limits);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_LIMITS_INVALID", "Plan limits must be valid JSON");
        }
    }

    private String normalizeLimitsJson(String value) {
        String normalized = value == null || value.isBlank() ? "{}" : value.trim();
        try {
            JsonNode node = objectMapper.readTree(normalized);
            if (!node.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_LIMITS_INVALID", "Plan limits must be a JSON object");
            }
            return objectMapper.writeValueAsString(node);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_LIMITS_INVALID", "Plan limits must be valid JSON");
        }
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeRequired(value, "status").toLowerCase();
        if (!"active".equals(normalized) && !"retired".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_STATUS_INVALID", "Plan status must be active or retired");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        String normalized = normalizeDefault(value, "EGP").toUpperCase();
        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_CURRENCY_INVALID", "Plan currency must be an ISO 4217 code");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_FIELD_REQUIRED", fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeDefault(String value, String defaultValue) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }
}
