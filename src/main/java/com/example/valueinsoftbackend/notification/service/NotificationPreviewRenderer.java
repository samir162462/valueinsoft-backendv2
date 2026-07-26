package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.repository.DbNotificationTemplate;
import com.ibm.icu.text.MessageFormat;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class NotificationPreviewRenderer {
    public String render(String policy,
                         DbNotificationTemplate.TemplateRow template,
                         Map<String, Object> params,
                         Locale locale,
                         int maxChars) {
        String value = switch (policy) {
            case "allowed" -> new MessageFormat(template.previewTemplate(), locale).format(params);
            case "generic_only" -> template.previewGeneric();
            case "disabled" -> "";
            default -> throw new IllegalArgumentException("Unknown preview policy: " + policy);
        };
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)) + "…";
    }
}
