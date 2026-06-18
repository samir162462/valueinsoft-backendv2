package com.example.valueinsoftbackend.Config;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SchemaCompatibilityInitializer implements ApplicationRunner {
    private static final int PASSWORD_COLUMN_LENGTH = 100;

    private final JdbcTemplate jdbcTemplate;

    public SchemaCompatibilityInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        widenUsersPasswordColumn("public");
        ensurePasswordResetRequiredColumn();

        List<String> tenantSchemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'c\\_%' ESCAPE '\\'",
                String.class
        );

        for (String schemaName : tenantSchemas) {
            widenUsersPasswordColumn(schemaName);
        }
    }

    private void widenUsersPasswordColumn(String schemaName) {
        Integer currentLength = jdbcTemplate.query(
                "SELECT character_maximum_length FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Object value = rs.getObject(1);
                    return value instanceof Number number ? number.intValue() : null;
                },
                schemaName,
                "users",
                "userPassword"
        );

        if (currentLength == null || currentLength >= PASSWORD_COLUMN_LENGTH) {
            return;
        }

        String validatedSchema = TenantSqlIdentifiers.requireSchemaName(schemaName);
        String sql = "ALTER TABLE " + validatedSchema + ".\"users\" ALTER COLUMN \"userPassword\" TYPE character varying(" +
                PASSWORD_COLUMN_LENGTH + ")";
        jdbcTemplate.execute(sql);
        log.info("Updated {}.users.userPassword to varchar({}) for BCrypt compatibility", schemaName, PASSWORD_COLUMN_LENGTH);
    }

    private void ensurePasswordResetRequiredColumn() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class,
                "public",
                "users",
                "password_reset_required"
        );

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE public.users ADD COLUMN password_reset_required BOOLEAN NOT NULL DEFAULT FALSE");
        log.info("Added public.users.password_reset_required for admin password reset lifecycle");
    }
}
