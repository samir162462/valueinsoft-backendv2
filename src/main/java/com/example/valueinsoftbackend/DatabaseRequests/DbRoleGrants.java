package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only access to role-to-capability grants.
 */
@Repository
public class DbRoleGrants {

    private static final RowMapper<RoleGrantConfig> ROLE_GRANT_ROW_MAPPER = (rs, rowNum) -> new RoleGrantConfig(
            rs.getString("role_id"),
            rs.getString("capability_key"),
            rs.getString("scope_type"),
            rs.getString("grant_mode"),
            rs.getString("grant_version")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbRoleGrants(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads all grants for the supplied role ids.
     */
    public List<RoleGrantConfig> getGrantsForRoleIds(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalizedRoleIds = new ArrayList<>();
        for (String roleId : roleIds) {
            if (roleId != null && !roleId.trim().isEmpty()) {
                normalizedRoleIds.add(roleId.trim());
            }
        }
        if (normalizedRoleIds.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(", ", Collections.nCopies(normalizedRoleIds.size(), "?"));
        String sql = "SELECT role_id, capability_key, scope_type, grant_mode, grant_version " +
                "FROM public.role_grants WHERE role_id IN (" + placeholders + ") " +
                "ORDER BY role_id ASC, capability_key ASC, scope_type ASC";
        return jdbcTemplate.query(sql, ROLE_GRANT_ROW_MAPPER, normalizedRoleIds.toArray());
    }
}
