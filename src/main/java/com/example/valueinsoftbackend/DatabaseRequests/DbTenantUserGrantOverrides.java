package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.TenantUserGrantOverrideConfig;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only access to user-specific capability overrides inside one tenant.
 */
@Repository
public class DbTenantUserGrantOverrides {

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

    public DbTenantUserGrantOverrides(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads user-specific capability overrides for one tenant and user.
     */
    public List<TenantUserGrantOverrideConfig> getUserGrantOverrides(int tenantId, int userId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(userId, "userId");
        String sql = "SELECT override_id, tenant_id, user_id, capability_key, grant_mode, scope_type, scope_branch_id, " +
                "reason, source FROM public.tenant_user_grant_overrides WHERE tenant_id = ? AND user_id = ? " +
                "ORDER BY capability_key ASC";
        return jdbcTemplate.query(sql, USER_GRANT_OVERRIDE_ROW_MAPPER, tenantId, userId);
    }
}
