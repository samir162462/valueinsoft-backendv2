package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.PlatformModuleConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only access to platform module definitions used by effective configuration resolution.
 */
@Repository
public class DbPlatformModules {

    private static final RowMapper<PlatformModuleConfig> MODULE_ROW_MAPPER = (rs, rowNum) -> new PlatformModuleConfig(
            rs.getString("module_id"),
            rs.getString("display_name"),
            rs.getString("category"),
            rs.getString("status"),
            rs.getBoolean("default_enabled"),
            rs.getString("config_version"),
            rs.getString("description")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbPlatformModules(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns every known platform module so resolution can apply package, template, and tenant state on top.
     */
    public List<PlatformModuleConfig> getAllModules() {
        String sql = "SELECT module_id, display_name, category, status, default_enabled, config_version, description " +
                "FROM public.platform_modules ORDER BY module_id ASC";
        return jdbcTemplate.query(sql, MODULE_ROW_MAPPER);
    }
}
