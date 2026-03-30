package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.TenantRoleAssignmentConfig;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only access to resolved tenant role assignments for users.
 */
@Repository
public class DbTenantRoleAssignments {

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

    private final JdbcTemplate jdbcTemplate;

    public DbTenantRoleAssignments(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads active role assignments for a user inside one tenant context.
     */
    public List<TenantRoleAssignmentConfig> getUserTenantRoleAssignments(int tenantId, int userId) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(userId, "userId");
        String sql = "SELECT assignment_id, tenant_id, user_id, role_id, status, assigned_at, assigned_by_user_id, " +
                "source, scope_type, scope_branch_id " +
                "FROM public.tenant_role_assignments WHERE tenant_id = ? AND user_id = ? AND status = 'active' " +
                "ORDER BY role_id ASC, assigned_at ASC";
        return jdbcTemplate.query(sql, ROLE_ASSIGNMENT_ROW_MAPPER, tenantId, userId);
    }
}
