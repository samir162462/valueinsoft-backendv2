package com.example.valueinsoftbackend.notification.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbNotificationTemplate {
    private final JdbcTemplate jdbc;

    public DbNotificationTemplate(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TemplateRow> findPublished(String typeKey, List<String> localeChain) {
        for (String locale : localeChain) {
            List<TemplateRow> rows = jdbc.query("""
                    SELECT locale, template_version, title_template, body_template,
                           preview_template, preview_generic
                    FROM public.notification_template
                    WHERE type_key = ? AND locale = ? AND status = 'published'
                    """, (rs, rowNum) -> new TemplateRow(
                    rs.getString("locale"), rs.getInt("template_version"),
                    rs.getString("title_template"), rs.getString("body_template"),
                    rs.getString("preview_template"), rs.getString("preview_generic")),
                    typeKey, locale);
            if (!rows.isEmpty()) {
                return Optional.of(rows.getFirst());
            }
        }
        return Optional.empty();
    }

    public record TemplateRow(
            String locale,
            int version,
            String titleTemplate,
            String bodyTemplate,
            String previewTemplate,
            String previewGeneric
    ) {}
}
