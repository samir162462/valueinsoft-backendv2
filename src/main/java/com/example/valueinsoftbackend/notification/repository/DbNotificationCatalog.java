package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DbNotificationCatalog {
    private static final String COLUMNS = """
            type_key, category, default_priority, push_preview_policy,
            group_key_template, aggregation_window_seconds, deep_link_template,
            required_capability, retention_days, preview_max_chars,
            default_channel_push, default_channel_in_app, is_user_mutable,
            bypasses_quiet_hours, producer_rate_limit_per_min
            """;

    private static final RowMapper<NotificationCatalogEntry> MAPPER = (rs, rowNum) -> {
        Integer rateLimit = rs.getObject("producer_rate_limit_per_min", Integer.class);
        return new NotificationCatalogEntry(
                rs.getString("type_key"),
                rs.getString("category"),
                rs.getString("default_priority"),
                rs.getString("push_preview_policy"),
                rs.getString("group_key_template"),
                rs.getInt("aggregation_window_seconds"),
                rs.getString("deep_link_template"),
                rs.getString("required_capability"),
                rs.getInt("retention_days"),
                rs.getInt("preview_max_chars"),
                rs.getBoolean("default_channel_push"),
                rs.getBoolean("default_channel_in_app"),
                rs.getBoolean("is_user_mutable"),
                rs.getBoolean("bypasses_quiet_hours"),
                rateLimit);
    };

    private final JdbcTemplate jdbc;

    public DbNotificationCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public NotificationCatalogEntry requireActive(String typeKey) {
        return jdbc.query(
                        "SELECT " + COLUMNS
                                + " FROM public.notification_type_catalog"
                                + " WHERE type_key = ? AND status = 'active'",
                        MAPPER, typeKey)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or inactive notification type: " + typeKey));
    }

    /**
     * Every active type, ordered for the preferences screen. Deprecated types are excluded:
     * a user should not be offered a switch for something that can no longer fire, but the
     * rows are never deleted because historical recipients still reference them.
     */
    public List<NotificationCatalogEntry> activeTypes() {
        return jdbc.query(
                "SELECT " + COLUMNS
                        + " FROM public.notification_type_catalog"
                        + " WHERE status = 'active'"
                        + " ORDER BY category, type_key",
                MAPPER);
    }
}
