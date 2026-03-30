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
            rs.getString("description")
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
        String sql = "SELECT package_id, display_name, status, price_code, config_version, description " +
                "FROM public.package_plans WHERE package_id = ?";
        List<PackagePlanConfig> results = jdbcTemplate.query(sql, PACKAGE_PLAN_ROW_MAPPER, normalizeId(packageId));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Loads the module policies that define which modules a package exposes and how they behave.
     */
    public List<PackageModulePolicy> getPackageModulePolicies(String packageId) {
        String sql = "SELECT package_id, module_id, enabled, mode, limits::text AS limits, policy_version " +
                "FROM public.package_module_policies WHERE package_id = ? ORDER BY module_id ASC";
        return jdbcTemplate.query(sql, PACKAGE_MODULE_POLICY_ROW_MAPPER, normalizeId(packageId));
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }
}
