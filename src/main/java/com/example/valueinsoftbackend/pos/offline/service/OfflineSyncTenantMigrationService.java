package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OfflineSyncTenantMigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final OfflinePosProperties properties;

    public OfflineSyncTenantMigrationService(JdbcTemplate jdbcTemplate, OfflinePosProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runIfEnabled() {
        if (!properties.isRunTenantMigrationOnStartup()) {
            log.debug("Offline sync tenant migration on startup is disabled");
            return;
        }

        List<String> schemas = jdbcTemplate.queryForList("""
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name LIKE 'c\\_%' ESCAPE '\\'
                ORDER BY schema_name
                """, String.class);

        for (String schema : schemas) {
            try {
                jdbcTemplate.query(
                        "SELECT public.create_offline_sync_tables_for_tenant(?)",
                        ps -> ps.setString(1, schema),
                        rs -> {
                        });
                log.info("Offline sync tenant tables verified for schema {}", schema);
            } catch (Exception ex) {
                log.warn("Failed to verify offline sync tenant tables for schema {}: {}",
                        schema, ex.getMessage());
            }
        }
    }
}
