package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.example.valueinsoftbackend.notification.repository.DbNotificationTemplate;
import com.ibm.icu.text.MessageFormat;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationTemplateRenderer {
    private final DbNotificationTemplate templates;
    private final NotificationPreviewRenderer previews;

    public NotificationTemplateRenderer(DbNotificationTemplate templates,
                                        NotificationPreviewRenderer previews) {
        this.templates = templates;
        this.previews = previews;
    }

    public RenderedNotification render(NotificationEvent event,
                                       NotificationCatalogEntry catalog,
                                       String requestedLocale) {
        List<String> localeChain = localeChain(requestedLocale);
        DbNotificationTemplate.TemplateRow template = templates
                .findPublished(event.typeKey(), localeChain)
                .orElseThrow(() -> new IllegalStateException(
                        "No published template for " + event.typeKey()));
        Locale locale = Locale.forLanguageTag(template.locale());
        Map<String, Object> params = event.params();
        try {
            String title = format(template.titleTemplate(), locale, params);
            String body = format(template.bodyTemplate(), locale, params);
            String preview = previews.render(catalog.pushPreviewPolicy(), template, params,
                    locale, catalog.previewMaxChars());
            String deepLink = format(catalog.deepLinkTemplate(), locale, params);
            String groupKey = event.groupKey() != null
                    ? event.groupKey()
                    : formatNullable(catalog.groupKeyTemplate(), locale, params);
            String status = template.locale().equals(localeChain.getFirst())
                    ? "ok" : "fallback_locale";
            return new RenderedNotification(title, body, preview, template.locale(),
                    template.version(), status, deepLink, groupKey,
                    template.previewGeneric());
        } catch (IllegalArgumentException ex) {
            throw new NotificationRenderException(event.typeKey(), template.locale(), ex);
        }
    }

    private static String format(String pattern, Locale locale, Map<String, Object> params) {
        return new MessageFormat(pattern, locale).format(params);
    }

    private static String formatNullable(String pattern, Locale locale, Map<String, Object> params) {
        return pattern == null ? null : format(pattern, locale, params);
    }

    private static List<String> localeChain(String requested) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (requested != null && !requested.isBlank()) {
            String normalized = Locale.forLanguageTag(requested).toLanguageTag();
            values.add(normalized);
            int separator = normalized.indexOf('-');
            if (separator > 0) {
                values.add(normalized.substring(0, separator));
            }
        }
        values.add("en");
        return new ArrayList<>(values);
    }

    public static class NotificationRenderException extends RuntimeException {
        public NotificationRenderException(String typeKey, String locale, Throwable cause) {
            super("Unable to render " + typeKey + " in " + locale, cause);
        }
    }
}
