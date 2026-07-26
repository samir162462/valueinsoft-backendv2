package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbNotificationCatalog {
    private final JdbcTemplate jdbc;

    public DbNotificationCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public NotificationCatalogEntry requireActive(String typeKey) {
        return jdbc.query("""
                        SELECT type_key, category, default_priority, push_preview_policy,
                               group_key_template, aggregation_window_seconds, deep_link_template,
                               required_capability, retention_days, preview_max_chars,
                               default_channel_push
                        FROM public.notification_type_catalog
                        WHERE type_key = ? AND status = 'active'
                        """,
                        (rs, rowNum) -> new NotificationCatalogEntry(
                                rs.getString("type_key"), rs.getString("category"),
                                rs.getString("default_priority"), rs.getString("push_preview_policy"),
                                rs.getString("group_key_template"),
                                rs.getInt("aggregation_window_seconds"),
                                rs.getString("deep_link_template"),
                                rs.getString("required_capability"), rs.getInt("retention_days"),
                                rs.getInt("preview_max_chars"),
                                rs.getBoolean("default_channel_push")),
                        typeKey)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or inactive notification type: " + typeKey));
    }
}
