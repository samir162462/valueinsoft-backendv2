package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.OnboardingStateConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantModuleOverrideConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantWorkflowOverrideConfig;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only access to tenant aggregates, tenant overrides, and onboarding state.
 */
@Repository
public class DbTenants {

    private static final RowMapper<TenantConfig> TENANT_ROW_MAPPER = (rs, rowNum) -> new TenantConfig(
            rs.getInt("tenant_id"),
            rs.getString("package_id"),
            rs.getString("template_id"),
            rs.getString("business_package_id"),
            rs.getString("status"),
            rs.getString("config_version"),
            rs.getString("legacy_plan_name"),
            rs.getString("bootstrap_source"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
    );

    private static final RowMapper<TenantModuleOverrideConfig> MODULE_OVERRIDE_ROW_MAPPER = (rs, rowNum) ->
            new TenantModuleOverrideConfig(
                    rs.getInt("tenant_id"),
                    rs.getString("module_id"),
                    rs.getBoolean("enabled"),
                    rs.getString("mode"),
                    rs.getString("reason"),
                    rs.getString("source"),
                    rs.getString("version")
            );

    private static final RowMapper<TenantWorkflowOverrideConfig> WORKFLOW_OVERRIDE_ROW_MAPPER = (rs, rowNum) ->
            new TenantWorkflowOverrideConfig(
                    rs.getInt("tenant_id"),
                    rs.getString("flag_key"),
                    rs.getString("flag_value"),
                    rs.getString("reason"),
                    rs.getString("source"),
                    rs.getString("version")
            );

    private static final RowMapper<OnboardingStateConfig> ONBOARDING_ROW_MAPPER = (rs, rowNum) ->
            new OnboardingStateConfig(
                    rs.getInt("tenant_id"),
                    rs.getString("status"),
                    rs.getString("current_step"),
                    rs.getString("completed_steps"),
                    rs.getString("required_next_action"),
                    rs.getString("diagnostics")
            );

    private final JdbcTemplate jdbcTemplate;

    public DbTenants(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads the tenant root record by tenant id.
     */
    public TenantConfig getTenantById(int tenantId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT tenant_id, package_id, template_id, business_package_id, status, config_version, legacy_plan_name, " +
                "bootstrap_source, created_at, updated_at FROM public.tenants WHERE tenant_id = ?";
        List<TenantConfig> results = jdbcTemplate.query(sql, TENANT_ROW_MAPPER, tenantId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Loads tenant-level module overrides applied after package and template defaults.
     */
    public List<TenantModuleOverrideConfig> getTenantModuleOverrides(int tenantId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT tenant_id, module_id, enabled, mode, reason, source, version " +
                "FROM public.tenant_module_overrides WHERE tenant_id = ? ORDER BY module_id ASC";
        return jdbcTemplate.query(sql, MODULE_OVERRIDE_ROW_MAPPER, tenantId);
    }

    /**
     * Loads tenant-level workflow flag overrides.
     */
    public List<TenantWorkflowOverrideConfig> getTenantWorkflowOverrides(int tenantId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT tenant_id, flag_key, flag_value::text AS flag_value, reason, source, version " +
                "FROM public.tenant_workflow_overrides WHERE tenant_id = ? ORDER BY flag_key ASC";
        return jdbcTemplate.query(sql, WORKFLOW_OVERRIDE_ROW_MAPPER, tenantId);
    }

    /**
     * Loads explicit onboarding state for the tenant when present.
     */
    public OnboardingStateConfig getOnboardingState(int tenantId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT tenant_id, status, current_step, completed_steps::text AS completed_steps, " +
                "required_next_action, diagnostics::text AS diagnostics " +
                "FROM public.onboarding_states WHERE tenant_id = ?";
        List<OnboardingStateConfig> results = jdbcTemplate.query(sql, ONBOARDING_ROW_MAPPER, tenantId);
        return results.isEmpty() ? null : results.get(0);
    }
}
