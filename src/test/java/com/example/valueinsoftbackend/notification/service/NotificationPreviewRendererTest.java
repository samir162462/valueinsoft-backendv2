package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.repository.DbNotificationTemplate;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreviewRendererTest {
    private final NotificationPreviewRenderer renderer = new NotificationPreviewRenderer();
    private final DbNotificationTemplate.TemplateRow template =
            new DbNotificationTemplate.TemplateRow(
                    "en", 2, "Title", "Body",
                    "{count, plural, one {One item} other {# items}}",
                    "Items need attention");

    @Test
    void allowedUsesIcuPluralRules() {
        assertThat(renderer.render("allowed", template, Map.of("count", 3),
                Locale.ENGLISH, 120)).isEqualTo("3 items");
    }

    @Test
    void genericAndDisabledNeverExposeParameters() {
        assertThat(renderer.render("generic_only", template,
                Map.of("secret", "private"), Locale.ENGLISH, 120))
                .isEqualTo("Items need attention");
        assertThat(renderer.render("disabled", template,
                Map.of("secret", "private"), Locale.ENGLISH, 120))
                .isEmpty();
    }
}
