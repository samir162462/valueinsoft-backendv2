package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.PackageModulePolicy;
import com.example.valueinsoftbackend.Model.Configuration.PackagePlanConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only access to commercial package plans and module policies.
 */
@Repository
public class DbPackagePlans {

    private static final RowMapper<PackagePlanConfig> PACKAGE_PLAN_ROW_MAPPER = (rs, rowNum) -> new PackagePlanConfig(
            rs.getString("package_id"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("price_code"),
            rs.getString("config_version"),
            rs.getString("description"),
            rs.getBigDecimal("monthly_price_amount"),
            rs.getString("currency_code"),
            rs.getInt("display_order"),
            rs.getBoolean("featured")
    );

    private static final RowMapper<PackageModulePolicy> PACKAGE_MODULE_POLICY_ROW_MAPPER = (rs, rowNum) -> new PackageModulePolicy(
            rs.getString("package_id"),
            rs.getString("module_id"),
            rs.getBoolean("enabled"),
            rs.getString("mode"),
            rs.getString("limits"),
            rs.getString("policy_version")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbPackagePlans(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads one package plan definition by its stable package id.
     */
    public PackagePlanConfig getPackagePlan(String packageId) {
        String sql = planSelectSql() +
                "FROM public.package_plans WHERE package_id = ?";
        List<PackagePlanConfig> results = jdbcTemplate.query(sql, PACKAGE_PLAN_ROW_MAPPER, normalizeId(packageId));
        return results.isEmpty() ? null : results.get(0);
    }

    public List<PackagePlanConfig> getAllPackagePlans(boolean activeOnly) {
        String whereClause = activeOnly ? "WHERE status = 'active' " : "";
        return jdbcTemplate.query(
                planSelectSql() + "FROM public.package_plans " + whereClause + "ORDER BY display_order ASC, package_id ASC",
                PACKAGE_PLAN_ROW_MAPPER
        );
    }

    /**
     * Loads the module policies that define which modules a package exposes and how they behave.
     */
    public List<PackageModulePolicy> getPackageModulePolicies(String packageId) {
        String sql = "SELECT package_id, module_id, enabled, mode, limits::text AS limits, policy_version " +
                "FROM public.package_module_policies WHERE package_id = ? ORDER BY module_id ASC";
        return jdbcTemplate.query(sql, PACKAGE_MODULE_POLICY_ROW_MAPPER, normalizeId(packageId));
    }

    public void updatePackagePlan(PackagePlanConfig plan) {
        jdbcTemplate.update(
                "UPDATE public.package_plans SET display_name = ?, status = ?, price_code = ?, config_version = ?, description = ?, " +
                        "monthly_price_amount = ?, currency_code = ?, display_order = ?, featured = ?, updated_at = NOW() " +
                        "WHERE package_id = ?",
                plan.getDisplayName(),
                plan.getStatus(),
                plan.getPriceCode(),
                plan.getConfigVersion(),
                plan.getDescription(),
                plan.getMonthlyPriceAmount(),
                plan.getCurrencyCode(),
                plan.getDisplayOrder(),
                plan.isFeatured(),
                normalizeId(plan.getPackageId())
        );
    }

    public void upsertPackageModulePolicy(String packageId,
                                          String moduleId,
                                          boolean enabled,
                                          String mode,
                                          String limitsJson,
                                          String policyVersion) {
        jdbcTemplate.update(
                "INSERT INTO public.package_module_policies (package_id, module_id, enabled, mode, limits, policy_version) " +
                        "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?) " +
                        "ON CONFLICT (package_id, module_id) DO UPDATE SET " +
                        "enabled = EXCLUDED.enabled, " +
                        "mode = EXCLUDED.mode, " +
                        "limits = EXCLUDED.limits, " +
                        "policy_version = EXCLUDED.policy_version, " +
                        "updated_at = NOW()",
                normalizeId(packageId),
                normalizeId(moduleId),
                enabled,
                normalizeNullable(mode),
                limitsJson == null || limitsJson.isBlank() ? "{}" : limitsJson,
                policyVersion == null || policyVersion.isBlank() ? "v1" : policyVersion.trim()
        );
    }

    private String planSelectSql() {
        return "SELECT package_id, display_name, status, price_code, config_version, description, " +
                "monthly_price_amount, currency_code, display_order, featured ";
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
