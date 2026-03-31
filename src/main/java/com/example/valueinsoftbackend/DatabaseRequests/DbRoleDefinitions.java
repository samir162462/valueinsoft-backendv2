package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.RoleDefinitionConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DbRoleDefinitions {

    private static final RowMapper<RoleDefinitionConfig> ROLE_DEFINITION_ROW_MAPPER = (rs, rowNum) ->
            new RoleDefinitionConfig(
                    rs.getString("role_id"),
                    rs.getString("display_name"),
                    rs.getString("role_type"),
                    rs.getString("status"),
                    rs.getString("description")
            );

    private final JdbcTemplate jdbcTemplate;

    public DbRoleDefinitions(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RoleDefinitionConfig> getActiveRoleDefinitions() {
        String sql = "SELECT role_id, display_name, role_type, status, description " +
                "FROM public.role_definitions WHERE status = 'active' ORDER BY display_name ASC";
        return jdbcTemplate.query(sql, ROLE_DEFINITION_ROW_MAPPER);
    }
}
