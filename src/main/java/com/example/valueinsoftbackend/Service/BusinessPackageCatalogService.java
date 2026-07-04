package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbBusinessPackages;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompanyTemplates;
import com.example.valueinsoftbackend.DatabaseRequests.DbConfigurationAdmin;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosCategory;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageCategoryConfig;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageConfig;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageGroupConfig;
import com.example.valueinsoftbackend.Model.Configuration.BusinessPackageSubcategoryConfig;
import com.example.valueinsoftbackend.Model.Configuration.CompanyTemplateConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformTenantBusinessPackageAssignmentResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.BusinessPackageCategoryRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.BusinessPackageGroupRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.BusinessPackageSubcategoryRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.BusinessPackageUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.TenantBusinessPackageAssignmentRequest;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import com.example.valueinsoftbackend.util.CustomPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BusinessPackageCatalogService {

    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbBusinessPackages dbBusinessPackages;
    private final DbCompanyTemplates dbCompanyTemplates;
    private final DbConfigurationAdmin dbConfigurationAdmin;
    private final DbTenants dbTenants;
    private final DbBranch dbBranch;
    private final DbPosCategory dbPosCategory;
    private final ObjectMapper objectMapper;

    public BusinessPackageCatalogService(PlatformAuthorizationService platformAuthorizationService,
                                         DbBusinessPackages dbBusinessPackages,
                                         DbCompanyTemplates dbCompanyTemplates,
                                         DbConfigurationAdmin dbConfigurationAdmin,
                                         DbTenants dbTenants,
                                         DbBranch dbBranch,
                                         DbPosCategory dbPosCategory,
                                         ObjectMapper objectMapper) {
        this.platformAuthorizationService = platformAuthorizationService;
        this.dbBusinessPackages = dbBusinessPackages;
        this.dbCompanyTemplates = dbCompanyTemplates;
        this.dbConfigurationAdmin = dbConfigurationAdmin;
        this.dbTenants = dbTenants;
        this.dbBranch = dbBranch;
        this.dbPosCategory = dbPosCategory;
        this.objectMapper = objectMapper;
    }

    public ArrayList<BusinessPackageConfig> getPublicBusinessPackages() {
        return buildPackages(dbBusinessPackages.getAllBusinessPackages(true));
    }

    public ArrayList<BusinessPackageConfig> getAdminBusinessPackages(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        return buildPackages(dbBusinessPackages.getAllBusinessPackages(false));
    }

    @Transactional
    public BusinessPackageConfig updateBusinessPackage(String authenticatedName,
                                                       String packageId,
                                                       BusinessPackageUpdateRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.write");
        CompanyTemplateConfig template = dbCompanyTemplates.getCompanyTemplate(normalizeRequired(request.getDefaultTemplateId(), "defaultTemplateId"));
        if (template == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_TEMPLATE_NOT_FOUND", "Company template not found");
        }

        BusinessPackageConfig next = new BusinessPackageConfig(
                normalizeRequired(packageId, "packageId"),
                normalizeRequired(request.getDisplayName(), "displayName"),
                normalizeNullable(request.getOnboardingLabel()),
                normalizeRequired(request.getBusinessType(), "businessType"),
                normalizeStatus(request.getStatus()),
                normalizeRequired(request.getConfigVersion(), "configVersion"),
                normalizeRequired(request.getDescription(), "description"),
                template.getTemplateId(),
                request.getDisplayOrder(),
                request.isFeatured(),
                new ArrayList<>()
        );
        dbBusinessPackages.upsertBusinessPackageRoot(next);
        dbBusinessPackages.deleteGroupsForPackage(next.getPackageId());

        ArrayList<BusinessPackageGroupConfig> groups = new ArrayList<>();
        for (BusinessPackageGroupRequest groupRequest : safeGroups(request.getGroups())) {
            BusinessPackageGroupConfig group = new BusinessPackageGroupConfig(
                    null,
                    normalizeRequired(groupRequest.getGroupKey(), "groupKey"),
                    normalizeRequired(groupRequest.getDisplayName(), "displayName"),
                    normalizeStatus(groupRequest.getStatus()),
                    groupRequest.getDisplayOrder(),
                    new ArrayList<>()
            );
            long groupId = dbBusinessPackages.insertGroup(next.getPackageId(), group);
            group.setGroupId(groupId);

            ArrayList<BusinessPackageCategoryConfig> categories = new ArrayList<>();
            for (BusinessPackageCategoryRequest categoryRequest : safeCategories(groupRequest.getCategories())) {
                BusinessPackageCategoryConfig category = new BusinessPackageCategoryConfig(
                        null,
                        normalizeRequired(categoryRequest.getCategoryKey(), "categoryKey"),
                        normalizeRequired(categoryRequest.getDisplayName(), "displayName"),
                        normalizeStatus(categoryRequest.getStatus()),
                        categoryRequest.getDisplayOrder(),
                        new ArrayList<>()
                );
                long categoryId = dbBusinessPackages.insertCategory(groupId, category);
                category.setCategoryId(categoryId);

                ArrayList<BusinessPackageSubcategoryConfig> subcategories = new ArrayList<>();
                for (BusinessPackageSubcategoryRequest subcategoryRequest : safeSubcategories(categoryRequest.getSubcategories())) {
                    BusinessPackageSubcategoryConfig subcategory = new BusinessPackageSubcategoryConfig(
                            null,
                            normalizeRequired(subcategoryRequest.getSubcategoryKey(), "subcategoryKey"),
                            normalizeRequired(subcategoryRequest.getDisplayName(), "displayName"),
                            normalizeStatus(subcategoryRequest.getStatus()),
                            subcategoryRequest.getDisplayOrder()
                    );
                    dbBusinessPackages.insertSubcategory(categoryId, subcategory);
                    subcategories.add(subcategory);
                }
                category.setSubcategories(subcategories);
                categories.add(category);
            }
            group.setCategories(categories);
            groups.add(group);
        }
        next.setGroups(groups);
        return requireBusinessPackage(next.getPackageId());
    }

    public PlatformTenantBusinessPackageAssignmentResponse getTenantAssignment(String authenticatedName, int tenantId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.read");
        TenantConfig tenant = requireTenant(tenantId);
        BusinessPackageConfig businessPackage = hasText(tenant.getBusinessPackageId())
                ? requireBusinessPackage(tenant.getBusinessPackageId())
                : null;
        return new PlatformTenantBusinessPackageAssignmentResponse(
                tenantId,
                tenant.getBusinessPackageId(),
                tenant.getBusinessPackageId(),
                false,
                new ArrayList<>(),
                businessPackage
        );
    }

    public BusinessPackageConfig getAssignedBusinessPackageForTenant(int tenantId) {
        TenantConfig tenant = requireTenant(tenantId);
        if (!hasText(tenant.getBusinessPackageId())) {
            return requireBusinessPackage(resolveBusinessPackageId(null));
        }
        return requireBusinessPackage(tenant.getBusinessPackageId());
    }

    @Transactional
    public PlatformTenantBusinessPackageAssignmentResponse assignTenantBusinessPackage(String authenticatedName,
                                                                                       int tenantId,
                                                                                       TenantBusinessPackageAssignmentRequest request) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.configuration.write");
        TenantConfig tenant = requireTenant(tenantId);
        BusinessPackageConfig businessPackage = requireBusinessPackage(request.getBusinessPackageId());
        String previousBusinessPackageId = tenant.getBusinessPackageId();
        dbConfigurationAdmin.updateTenantBusinessPackage(tenantId, businessPackage.getPackageId());

        ArrayList<Integer> reseededBranchIds = new ArrayList<>();
        if (request.isReseedBranches()) {
            for (Branch branch : dbBranch.getBranchByCompanyId(tenantId)) {
                if (branch != null && branch.getBranchID() > 0 && provisionBranchCategories(tenantId, branch.getBranchID(), true)) {
                    reseededBranchIds.add(branch.getBranchID());
                }
            }
        }

        return new PlatformTenantBusinessPackageAssignmentResponse(
                tenantId,
                businessPackage.getPackageId(),
                previousBusinessPackageId,
                request.isReseedBranches(),
                reseededBranchIds,
                businessPackage
        );
    }

    @Transactional
    public void bootstrapTenantForNewCompany(int tenantId,
                                             String commercialPackageId,
                                             String businessPackageId,
                                             String legacyPlanName,
                                             int ownerUserId) {
        BusinessPackageConfig businessPackage = requireBusinessPackage(resolveBusinessPackageId(businessPackageId));
        dbConfigurationAdmin.upsertTenant(
                tenantId,
                normalizeRequired(commercialPackageId, "commercialPackageId"),
                businessPackage.getDefaultTemplateId(),
                businessPackage.getPackageId(),
                "onboarding",
                "v1",
                normalizeNullable(legacyPlanName),
                "api"
        );
        dbConfigurationAdmin.upsertOnboardingState(
                tenantId,
                "in_progress",
                "branch_setup",
                "[\"company_created\"]",
                "create_first_branch",
                "{}"
        );
        dbConfigurationAdmin.upsertTenantRoleAssignment(
                tenantId,
                ownerUserId,
                "Owner",
                "company",
                null,
                ownerUserId,
                "bootstrap"
        );
    }

    @Transactional
    public boolean provisionBranchCategoriesIfMissing(int companyId, int branchId) {
        return provisionBranchCategories(companyId, branchId, false);
    }

    @Transactional
    public boolean provisionBranchCategories(int companyId, int branchId, boolean force) {
        TenantConfig tenant = dbTenants.getTenantById(companyId);
        if (tenant == null || !hasText(tenant.getBusinessPackageId())) {
            return false;
        }

        String existingPayload = dbPosCategory.getCategoryJson(branchId, companyId);
        if (!force && existingPayload != null && !existingPayload.isBlank()) {
            return false;
        }

        BusinessPackageConfig businessPackage = requireBusinessPackage(tenant.getBusinessPackageId());
        Map<String, Object> payload = buildBranchTaxonomySnapshot(businessPackage);
        if (((List<?>) payload.getOrDefault("groups", List.of())).isEmpty()) {
            return false;
        }

        try {
            int rows = dbPosCategory.saveCategoryJson(companyId, branchId, objectMapper.writeValueAsString(payload));
            return rows == 1;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_PACKAGE_CATEGORY_SEED_FAILED", "Unable to seed branch categories from the assigned business package");
        }
    }

    public String resolveBusinessPackageId(String requestedValue) {
        if (hasText(requestedValue)) {
            return requestedValue.trim();
        }
        BusinessPackageConfig featured = dbBusinessPackages.getFeaturedBusinessPackage();
        if (featured != null && hasText(featured.getPackageId())) {
            return featured.getPackageId();
        }
        return "mobile_shop";
    }

    public String mapLegacyBranchMajorToBusinessPackage(String branchMajor) {
        if (!hasText(branchMajor)) {
            return resolveBusinessPackageId(null);
        }
        String normalized = branchMajor.trim().toLowerCase().replace(' ', '_');
        if (normalized.contains("car")) {
            return "car_workshop";
        }
        return resolveBusinessPackageId(null);
    }

    private ArrayList<BusinessPackageConfig> buildPackages(List<BusinessPackageConfig> roots) {
        ArrayList<BusinessPackageConfig> items = new ArrayList<>();
        for (BusinessPackageConfig root : roots) {
            items.add(requireBusinessPackage(root.getPackageId()));
        }
        return items;
    }

    private BusinessPackageConfig requireBusinessPackage(String packageId) {
        BusinessPackageConfig root = dbBusinessPackages.getBusinessPackage(packageId);
        if (root == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BUSINESS_PACKAGE_NOT_FOUND", "Business package not found");
        }

        ArrayList<BusinessPackageGroupConfig> groups = new ArrayList<>(dbBusinessPackages.getGroups(packageId));
        Map<Long, BusinessPackageGroupConfig> groupsById = new LinkedHashMap<>();
        for (BusinessPackageGroupConfig group : groups) {
            group.setCategories(new ArrayList<>());
            groupsById.put(group.getGroupId(), group);
        }

        List<Map<String, Object>> categoryLinks = dbBusinessPackages.getCategoryGroupLinks(packageId);
        List<BusinessPackageCategoryConfig> categories = dbBusinessPackages.getCategories(packageId);
        Map<Long, BusinessPackageCategoryConfig> categoriesById = new LinkedHashMap<>();
        for (BusinessPackageCategoryConfig category : categories) {
            category.setSubcategories(new ArrayList<>());
            categoriesById.put(category.getCategoryId(), category);
        }
        for (int index = 0; index < categoryLinks.size() && index < categories.size(); index++) {
            Long groupId = (Long) categoryLinks.get(index).get("groupId");
            BusinessPackageGroupConfig group = groupsById.get(groupId);
            if (group != null) {
                group.getCategories().add(categories.get(index));
            }
        }

        List<Map<String, Object>> subcategoryLinks = dbBusinessPackages.getSubcategoryCategoryLinks(packageId);
        List<BusinessPackageSubcategoryConfig> subcategories = dbBusinessPackages.getSubcategories(packageId);
        for (int index = 0; index < subcategoryLinks.size() && index < subcategories.size(); index++) {
            Long categoryId = (Long) subcategoryLinks.get(index).get("categoryId");
            BusinessPackageCategoryConfig category = categoriesById.get(categoryId);
            if (category != null) {
                category.getSubcategories().add(subcategories.get(index));
            }
        }

        root.setGroups(groups);
        return root;
    }

    private TenantConfig requireTenant(int tenantId) {
        TenantConfig tenant = dbTenants.getTenantById(tenantId);
        if (tenant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }
        return tenant;
    }

    private ArrayList<CustomPair> flattenBusinessPackageToPairs(BusinessPackageConfig businessPackage) {
        ArrayList<CustomPair> pairs = new ArrayList<>();
        if (businessPackage == null || businessPackage.getGroups() == null) {
            return pairs;
        }

        for (BusinessPackageGroupConfig group : businessPackage.getGroups()) {
            if (group == null || group.getCategories() == null || !"active".equalsIgnoreCase(group.getStatus())) {
                continue;
            }
            for (BusinessPackageCategoryConfig category : group.getCategories()) {
                if (category == null || !"active".equalsIgnoreCase(category.getStatus())) {
                    continue;
                }
                ArrayList<String> subcategoryNames = new ArrayList<>();
                for (BusinessPackageSubcategoryConfig subcategory : category.getSubcategories()) {
                    if (subcategory != null && "active".equalsIgnoreCase(subcategory.getStatus()) && hasText(subcategory.getDisplayName())) {
                        subcategoryNames.add(subcategory.getDisplayName().trim());
                    }
                }
                pairs.add(new CustomPair(category.getDisplayName(), subcategoryNames));
            }
        }
        return pairs;
    }

    private Map<String, Object> buildBranchTaxonomySnapshot(BusinessPackageConfig businessPackage) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        ArrayList<Map<String, Object>> groups = new ArrayList<>();
        ArrayList<Map<String, Object>> legacyCategoryData = new ArrayList<>();
        ArrayList<Map<String, Object>> legacyBranchGroups = new ArrayList<>();

        if (businessPackage == null || businessPackage.getGroups() == null) {
            snapshot.put("schemaVersion", 2);
            snapshot.put("groups", groups);
            snapshot.put("categoryData", legacyCategoryData);
            snapshot.put("branchGroups", legacyBranchGroups);
            return snapshot;
        }

        Set<String> usedGroupKeys = new HashSet<>();
        for (BusinessPackageGroupConfig groupConfig : businessPackage.getGroups()) {
            if (groupConfig == null || groupConfig.getCategories() == null || !"active".equalsIgnoreCase(groupConfig.getStatus())) {
                continue;
            }

            String groupLabel = firstNonBlank(groupConfig.getDisplayName(), groupConfig.getGroupKey());
            if (!hasText(groupLabel)) {
                continue;
            }
            String groupKey = uniqueTaxonomyKey(firstNonBlank(groupConfig.getGroupKey(), groupLabel), usedGroupKeys);
            Set<String> usedCategoryKeys = new HashSet<>();
            ArrayList<Map<String, Object>> categories = new ArrayList<>();
            ArrayList<String> legacyGroupCategories = new ArrayList<>();

            for (BusinessPackageCategoryConfig categoryConfig : groupConfig.getCategories()) {
                if (categoryConfig == null || categoryConfig.getSubcategories() == null || !"active".equalsIgnoreCase(categoryConfig.getStatus())) {
                    continue;
                }

                String categoryLabel = firstNonBlank(categoryConfig.getDisplayName(), categoryConfig.getCategoryKey());
                if (!hasText(categoryLabel)) {
                    continue;
                }
                String categoryKey = uniqueTaxonomyKey(firstNonBlank(categoryConfig.getCategoryKey(), categoryLabel), usedCategoryKeys);
                Set<String> usedSubcategoryKeys = new HashSet<>();
                ArrayList<Map<String, Object>> subcategories = new ArrayList<>();
                ArrayList<String> legacySubcategories = new ArrayList<>();

                for (BusinessPackageSubcategoryConfig subcategoryConfig : categoryConfig.getSubcategories()) {
                    if (subcategoryConfig == null || !"active".equalsIgnoreCase(subcategoryConfig.getStatus())) {
                        continue;
                    }
                    String subcategoryLabel = firstNonBlank(subcategoryConfig.getDisplayName(), subcategoryConfig.getSubcategoryKey());
                    if (!hasText(subcategoryLabel)) {
                        continue;
                    }
                    Map<String, Object> subcategory = new LinkedHashMap<>();
                    subcategory.put("key", uniqueTaxonomyKey(firstNonBlank(subcategoryConfig.getSubcategoryKey(), subcategoryLabel), usedSubcategoryKeys));
                    subcategory.put("label", subcategoryLabel.trim());
                    subcategories.add(subcategory);
                    legacySubcategories.add(subcategoryLabel.trim());
                }

                Map<String, Object> category = new LinkedHashMap<>();
                category.put("key", categoryKey);
                category.put("label", categoryLabel.trim());
                category.put("subcategories", subcategories);
                categories.add(category);
                legacyGroupCategories.add(categoryLabel.trim());

                Map<String, Object> legacyCategory = new LinkedHashMap<>();
                legacyCategory.put("key", categoryLabel.trim());
                legacyCategory.put("value", legacySubcategories);
                legacyCategoryData.add(legacyCategory);
            }

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("key", groupKey);
            group.put("label", groupLabel.trim());
            group.put("categories", categories);
            groups.add(group);

            Map<String, Object> legacyGroup = new LinkedHashMap<>();
            legacyGroup.put("key", groupLabel.trim());
            legacyGroup.put("categories", legacyGroupCategories);
            legacyBranchGroups.add(legacyGroup);
        }

        snapshot.put("schemaVersion", 2);
        snapshot.put("groups", groups);
        snapshot.put("categoryData", legacyCategoryData);
        snapshot.put("branchGroups", legacyBranchGroups);
        return snapshot;
    }

    private String uniqueTaxonomyKey(String value, Set<String> usedKeys) {
        String base = slugifyTaxonomyKey(value);
        String candidate = base;
        int suffix = 2;
        while (usedKeys.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        usedKeys.add(candidate);
        return candidate;
    }

    private String slugifyTaxonomyKey(String value) {
        String text = value == null ? "item" : value.trim().toLowerCase(java.util.Locale.ROOT);
        text = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        text = text.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return text.isBlank() ? "item" : text;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : (hasText(fallback) ? fallback.trim() : null);
    }

    private ArrayList<BusinessPackageGroupRequest> safeGroups(ArrayList<BusinessPackageGroupRequest> value) {
        return value == null ? new ArrayList<>() : value;
    }

    private ArrayList<BusinessPackageCategoryRequest> safeCategories(ArrayList<BusinessPackageCategoryRequest> value) {
        return value == null ? new ArrayList<>() : value;
    }

    private ArrayList<BusinessPackageSubcategoryRequest> safeSubcategories(ArrayList<BusinessPackageSubcategoryRequest> value) {
        return value == null ? new ArrayList<>() : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUSINESS_PACKAGE_FIELD_REQUIRED", fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeRequired(value, "status").toLowerCase();
        if (!"active".equals(normalized) && !"retired".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUSINESS_PACKAGE_STATUS_INVALID", "Status must be active or retired");
        }
        return normalized;
    }
}
