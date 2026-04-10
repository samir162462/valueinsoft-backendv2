package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.ConfigurationAdminUserSummary;
import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DbConfigurationAdmin {

    private static final RowMapper<ConfigurationAdminUserSummary> USER_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new ConfigurationAdminUserSummary(
                    rs.getInt("user_id"),
                    rs.getString("user_name"),
                    rs.getString("email"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("user_phone"),
                    rs.getString("legacy_role"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getTimestamp("creation_time")
            );

    private static final RowMapper<TenantRoleAssignmentConfig> ROLE_ASSIGNMENT_ROW_MAPPER = (rs, rowNum) ->
            new TenantRoleAssignmentConfig(
                    rs.getLong("assignment_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("user_id"),
                    rs.getString("role_id"),
                    rs.getString("status"),
                    rs.getTimestamp("assigned_at"),
                    (Integer) rs.getObject("assigned_by_user_id"),
                    rs.getString("source"),
                    rs.getString("scope_type"),
                    (Integer) rs.getObject("scope_branch_id")
            );

    private static final RowMapper<TenantUserGrantOverrideConfig> USER_GRANT_OVERRIDE_ROW_MAPPER = (rs, rowNum) ->
            new TenantUserGrantOverrideConfig(
                    rs.getLong("override_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("user_id"),
                    rs.getString("capability_key"),
                    rs.getString("grant_mode"),
                    rs.getString("scope_type"),
                    (Integer) rs.getObject("scope_branch_id"),
                    rs.getString("reason"),
                    rs.getString("source")
            );

    private final JdbcTemplate jdbcTemplate;

    public DbConfigurationAdmin(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConfigurationAdminUserSummary> getUsersForTenant(int tenantId, Integer branchId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT u.id AS user_id, u.\"userName\" AS user_name, u.\"userEmail\" AS email, " +
                "u.\"firstName\" AS first_name, u.\"lastName\" AS last_name, u.\"userPhone\" AS user_phone, " +
                "u.\"userRole\" AS legacy_role, u.\"branchId\" AS branch_id, b.\"branchName\" AS branch_name, " +
                "u.\"creationTime\" AS creation_time " +
                "FROM public.users u " +
                "JOIN public.\"Branch\" b ON b.\"branchId\" = u.\"branchId\" " +
                "WHERE b.\"companyId\" = ? ";
        if (branchId != null) {
            TenantSqlIdentifiers.requirePositive(branchId, "branchId");
            sql += "AND b.\"branchId\" = ? ORDER BY b.\"branchName\" ASC, u.\"userName\" ASC";
            return jdbcTemplate.query(sql, USER_SUMMARY_ROW_MAPPER, tenantId, branchId);
        }
        sql += "ORDER BY b.\"branchName\" ASC, u.\"userName\" ASC";
        return jdbcTemplate.query(sql, USER_SUMMARY_ROW_MAPPER, tenantId);
    }

    public List<TenantRoleAssignmentConfig> getTenantRoleAssignments(int tenantId, Integer branchId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT assignment_id, tenant_id, user_id, role_id, status, assigned_at, assigned_by_user_id, " +
                "source, scope_type, scope_branch_id FROM public.tenant_role_assignments WHERE tenant_id = ? AND status = 'active' ";
        if (branchId != null) {
            TenantSqlIdentifiers.requirePositive(branchId, "branchId");
            sql += "AND (scope_type <> 'branch' OR scope_branch_id = ?) ORDER BY user_id ASC, role_id ASC, assigned_at ASC";
            return jdbcTemplate.query(sql, ROLE_ASSIGNMENT_ROW_MAPPER, tenantId, branchId);
        }
        sql += "ORDER BY user_id ASC, role_id ASC, assigned_at ASC";
        return jdbcTemplate.query(sql, ROLE_ASSIGNMENT_ROW_MAPPER, tenantId);
    }

    public List<TenantUserGrantOverrideConfig> getTenantUserGrantOverrides(int tenantId, Integer branchId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "SELECT override_id, tenant_id, user_id, capability_key, grant_mode, scope_type, scope_branch_id, " +
                "reason, source FROM public.tenant_user_grant_overrides WHERE tenant_id = ? ";
        if (branchId != null) {
            TenantSqlIdentifiers.requirePositive(branchId, "branchId");
            sql += "AND (scope_type <> 'branch' OR scope_branch_id = ?) ORDER BY user_id ASC, capability_key ASC";
            return jdbcTemplate.query(sql, USER_GRANT_OVERRIDE_ROW_MAPPER, tenantId, branchId);
        }
        sql += "ORDER BY user_id ASC, capability_key ASC";
        return jdbcTemplate.query(sql, USER_GRANT_OVERRIDE_ROW_MAPPER, tenantId);
    }

    public void upsertTenantModuleOverride(int tenantId,
                                           String moduleId,
                                           boolean enabled,
                                           String mode,
                                           String reason,
                                           String source,
                                           String version) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "INSERT INTO public.tenant_module_overrides " +
                "(tenant_id, module_id, enabled, mode, reason, source, version) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id, module_id) DO UPDATE SET enabled = EXCLUDED.enabled, mode = EXCLUDED.mode, " +
                "reason = EXCLUDED.reason, source = EXCLUDED.source, version = EXCLUDED.version, updated_at = NOW()";
        jdbcTemplate.update(sql, tenantId, moduleId, enabled, mode, reason, source, version);
    }

    public void upsertTenant(int tenantId,
                             String packageId,
                             String templateId,
                             String businessPackageId,
                             String status,
                             String configVersion,
                             String legacyPlanName,
                             String bootstrapSource) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "INSERT INTO public.tenants " +
                "(tenant_id, package_id, template_id, business_package_id, status, config_version, legacy_plan_name, bootstrap_source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (tenant_id) DO UPDATE SET package_id = EXCLUDED.package_id, template_id = EXCLUDED.template_id, " +
                "business_package_id = EXCLUDED.business_package_id, status = EXCLUDED.status, config_version = EXCLUDED.config_version, " +
                "legacy_plan_name = EXCLUDED.legacy_plan_name, bootstrap_source = EXCLUDED.bootstrap_source, updated_at = NOW()";
        jdbcTemplate.update(sql, tenantId, packageId, templateId, businessPackageId, status, configVersion, legacyPlanName, bootstrapSource);
    }

    public void updateTenantBusinessPackage(int tenantId, String businessPackageId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        jdbcTemplate.update(
                "UPDATE public.tenants SET business_package_id = ?, updated_at = NOW() WHERE tenant_id = ?",
                businessPackageId,
                tenantId
        );
    }

    public void upsertOnboardingState(int tenantId,
                                      String status,
                                      String currentStep,
                                      String completedStepsJson,
                                      String requiredNextAction,
                                      String diagnosticsJson) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "INSERT INTO public.onboarding_states " +
                "(tenant_id, status, current_step, completed_steps, required_next_action, diagnostics) " +
                "VALUES (?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb)) " +
                "ON CONFLICT (tenant_id) DO UPDATE SET status = EXCLUDED.status, current_step = EXCLUDED.current_step, " +
                "completed_steps = EXCLUDED.completed_steps, required_next_action = EXCLUDED.required_next_action, diagnostics = EXCLUDED.diagnostics, updated_at = NOW()";
        jdbcTemplate.update(sql, tenantId, status, currentStep, completedStepsJson, requiredNextAction, diagnosticsJson);
    }

    public void upsertTenantWorkflowOverride(int tenantId,
                                             String flagKey,
                                             String flagValueJson,
                                             String reason,
                                             String source,
                                             String version) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        String sql = "INSERT INTO public.tenant_workflow_overrides " +
                "(tenant_id, flag_key, flag_value, reason, source, version) VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?) " +
                "ON CONFLICT (tenant_id, flag_key) DO UPDATE SET flag_value = EXCLUDED.flag_value, reason = EXCLUDED.reason, " +
                "source = EXCLUDED.source, version = EXCLUDED.version, updated_at = NOW()";
        jdbcTemplate.update(sql, tenantId, flagKey, flagValueJson, reason, source, version);
    }

    public void upsertTenantRoleAssignment(int tenantId,
                                           int userId,
                                           String roleId,
                                           String scopeType,
                                           Integer scopeBranchId,
                                           Integer assignedByUserId,
                                           String source) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(userId, "userId");
        if (scopeBranchId != null) {
            TenantSqlIdentifiers.requirePositive(scopeBranchId, "scopeBranchId");
        }

        int rows;
        if (scopeBranchId == null) {
            String updateSql = "UPDATE public.tenant_role_assignments SET status = 'active', assigned_at = NOW(), " +
                    "assigned_by_user_id = ?, source = ? WHERE tenant_id = ? AND user_id = ? AND role_id = ? " +
                    "AND scope_type = ? AND scope_branch_id IS NULL";
            rows = jdbcTemplate.update(updateSql, assignedByUserId, source, tenantId, userId, roleId, scopeType);
            if (rows == 0) {
                String insertSql = "INSERT INTO public.tenant_role_assignments " +
                        "(tenant_id, user_id, role_id, status, assigned_at, assigned_by_user_id, source, scope_type, scope_branch_id) " +
                        "VALUES (?, ?, ?, 'active', NOW(), ?, ?, ?, NULL)";
                jdbcTemplate.update(insertSql, tenantId, userId, roleId, assignedByUserId, source, scopeType);
            }
            return;
        }

        String updateSql = "UPDATE public.tenant_role_assignments SET status = 'active', assigned_at = NOW(), " +
                "assigned_by_user_id = ?, source = ? WHERE tenant_id = ? AND user_id = ? AND role_id = ? " +
                "AND scope_type = ? AND scope_branch_id = ?";
        rows = jdbcTemplate.update(updateSql, assignedByUserId, source, tenantId, userId, roleId, scopeType, scopeBranchId);
        if (rows == 0) {
            String insertSql = "INSERT INTO public.tenant_role_assignments " +
                    "(tenant_id, user_id, role_id, status, assigned_at, assigned_by_user_id, source, scope_type, scope_branch_id) " +
                    "VALUES (?, ?, ?, 'active', NOW(), ?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, tenantId, userId, roleId, assignedByUserId, source, scopeType, scopeBranchId);
        }
    }

    public void deactivateTenantRoleAssignment(long assignmentId) {
        String sql = "UPDATE public.tenant_role_assignments SET status = 'inactive' WHERE assignment_id = ?";
        jdbcTemplate.update(sql, assignmentId);
    }

    public void upsertTenantUserGrantOverride(int tenantId,
                                              int userId,
                                              String capabilityKey,
                                              String grantMode,
                                              String scopeType,
                                              Integer scopeBranchId,
                                              String reason,
                                              String source) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(userId, "userId");
        if (scopeBranchId != null) {
            TenantSqlIdentifiers.requirePositive(scopeBranchId, "scopeBranchId");
        }

        int rows;
        if (scopeBranchId == null) {
            String updateSql = "UPDATE public.tenant_user_grant_overrides SET grant_mode = ?, reason = ?, source = ?, updated_at = NOW() " +
                    "WHERE tenant_id = ? AND user_id = ? AND capability_key = ? AND scope_type = ? AND scope_branch_id IS NULL";
            rows = jdbcTemplate.update(updateSql, grantMode, reason, source, tenantId, userId, capabilityKey, scopeType);
            if (rows == 0) {
                String insertSql = "INSERT INTO public.tenant_user_grant_overrides " +
                        "(tenant_id, user_id, capability_key, grant_mode, scope_type, scope_branch_id, reason, source) " +
                        "VALUES (?, ?, ?, ?, ?, NULL, ?, ?)";
                jdbcTemplate.update(insertSql, tenantId, userId, capabilityKey, grantMode, scopeType, reason, source);
            }
            return;
        }

        String updateSql = "UPDATE public.tenant_user_grant_overrides SET grant_mode = ?, reason = ?, source = ?, updated_at = NOW() " +
                "WHERE tenant_id = ? AND user_id = ? AND capability_key = ? AND scope_type = ? AND scope_branch_id = ?";
        rows = jdbcTemplate.update(updateSql, grantMode, reason, source, tenantId, userId, capabilityKey, scopeType, scopeBranchId);
        if (rows == 0) {
            String insertSql = "INSERT INTO public.tenant_user_grant_overrides " +
                    "(tenant_id, user_id, capability_key, grant_mode, scope_type, scope_branch_id, reason, source) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, tenantId, userId, capabilityKey, grantMode, scopeType, scopeBranchId, reason, source);
        }
    }

    public void deleteTenantUserGrantOverride(int tenantId,
                                              int userId,
                                              String capabilityKey,
                                              String scopeType,
                                              Integer scopeBranchId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(userId, "userId");
        if (scopeBranchId == null) {
            String sql = "DELETE FROM public.tenant_user_grant_overrides WHERE tenant_id = ? AND user_id = ? " +
                    "AND capability_key = ? AND scope_type = ? AND scope_branch_id IS NULL";
            jdbcTemplate.update(sql, tenantId, userId, capabilityKey, scopeType);
            return;
        }
        String sql = "DELETE FROM public.tenant_user_grant_overrides WHERE tenant_id = ? AND user_id = ? " +
                "AND capability_key = ? AND scope_type = ? AND scope_branch_id = ?";
        jdbcTemplate.update(sql, tenantId, userId, capabilityKey, scopeType, scopeBranchId);
    }
}
