package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DbPlatformCapabilities {

    private static final RowMapper<PlatformCapabilityConfig> PLATFORM_CAPABILITY_ROW_MAPPER = (rs, rowNum) ->
            new PlatformCapabilityConfig(
                    rs.getString("capability_key"),
                    rs.getString("module_id"),
                    rs.getString("resource"),
                    rs.getString("action"),
                    rs.getString("scope_type"),
                    rs.getString("status"),
                    rs.getString("description")
            );

    private final JdbcTemplate jdbcTemplate;

    public DbPlatformCapabilities(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlatformCapabilityConfig> getActiveCapabilities() {
        String sql = "SELECT capability_key, module_id, resource, action, scope_type, status, description " +
                "FROM public.platform_capabilities WHERE status = 'active' ORDER BY module_id ASC, capability_key ASC";
        return jdbcTemplate.query(sql, PLATFORM_CAPABILITY_ROW_MAPPER);
    }

    public PlatformCapabilityConfig getCapability(String capabilityKey) {
        String sql = "SELECT capability_key, module_id, resource, action, scope_type, status, description " +
                "FROM public.platform_capabilities WHERE capability_key = ?";
        List<PlatformCapabilityConfig> results = jdbcTemplate.query(sql, PLATFORM_CAPABILITY_ROW_MAPPER, capabilityKey);
        return results.isEmpty() ? null : results.get(0);
    }
}
