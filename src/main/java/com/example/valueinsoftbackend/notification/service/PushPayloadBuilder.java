package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PushPayloadBuilder {
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final DistributionSummary payloadBytes;

    public PushPayloadBuilder(NotificationProperties properties,
                              ObjectMapper objectMapper,
                              MeterRegistry meters) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.payloadBytes = DistributionSummary.builder("notification.push.payload_bytes")
                .baseUnit("bytes")
                .register(meters);
    }

    public BuiltPush build(String provider,
                           long companyId,
                           int userId,
                           UUID recipientUuid,
                           int aggregateCount,
                           NotificationEvent event,
                           NotificationCatalogEntry catalog,
                           RenderedNotification rendered,
                           int payloadVersion) {
        String preview = rendered.preview();
        boolean includeOptional = true;
        int step = 0;
        while (true) {
            Map<String, Object> payload = providerPayload(
                    provider, companyId, userId, recipientUuid, aggregateCount,
                    event, catalog, rendered, preview, includeOptional, payloadVersion);
            String json = json(payload);
            int bytes = json.getBytes(StandardCharsets.UTF_8).length;
            if (bytes <= properties.getPayload().getMaxBytes()) {
                payloadBytes.record(bytes);
                return new BuiltPush(payload, json, bytes, step, false);
            }
            step++;
            switch (step) {
                case 1 -> preview = truncateWord(
                        preview, catalog.previewMaxChars());
                case 2 -> includeOptional = false;
                case 3 -> preview = truncateWord(
                        preview, properties.getPayload().getPreviewFallbackChars());
                case 4 -> preview = rendered.previewGeneric();
                case 5 -> preview = null;
                default -> {
                    meters.counter("notification.push.payload_rejected",
                            "type", event.typeKey()).increment();
                    return new BuiltPush(Map.of(), "{}", 2, 6, true);
                }
            }
            meters.counter("notification.push.payload_truncated",
                    "type", event.typeKey(), "step", Integer.toString(step)).increment();
        }
    }

    private Map<String, Object> providerPayload(String provider,
                                                long companyId,
                                                int userId,
                                                UUID recipientUuid,
                                                int aggregateCount,
                                                NotificationEvent event,
                                                NotificationCatalogEntry catalog,
                                                RenderedNotification rendered,
                                                String preview,
                                                boolean includeOptional,
                                                int payloadVersion) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyId", Long.toString(companyId));
        data.put("userId", Integer.toString(userId));
        data.put("recipientUuid", recipientUuid.toString());
        data.put("typeKey", event.typeKey());
        data.put("category", catalog.category());
        data.put("route", rendered.deepLink());
        data.put("payloadVersion", Integer.toString(payloadVersion));
        if (includeOptional) {
            data.put("aggregateCount", Integer.toString(aggregateCount));
            data.put("sentAt", Instant.now().toString());
        }

        if ("fcm".equals(provider)) {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("title", rendered.title());
            if (preview != null && !preview.isBlank()) {
                notification.put("body", preview);
            }
            result.put("notification", notification);
            result.put("data", data);
            result.put("android", Map.of(
                    "priority", "critical".equals(event.priority()) ? "high" : "normal",
                    "collapse_key", recipientUuid.toString(),
                    "ttl", "86400s",
                    "notification", Map.of(
                            "channel_id", "vls_" + catalog.category(),
                            "tag", recipientUuid.toString())));
            return result;
        }
        if (!"apns".equals(provider)) {
            throw new IllegalArgumentException("Unsupported push provider: " + provider);
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", rendered.title());
        if (preview != null && !preview.isBlank()) {
            alert.put("body", preview);
        }
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("thread-id", recipientUuid.toString());
        aps.put("category", "VLS_" + catalog.category().toUpperCase());
        aps.put("sound", "default");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aps", aps);
        result.put("vls", data);
        return result;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize push payload", exception);
        }
    }

    static String truncateWord(String value, int maxCharacters) {
        if (value == null || value.length() <= maxCharacters) {
            return value;
        }
        if (maxCharacters <= 1) {
            return "…";
        }
        int boundary = value.lastIndexOf(' ', maxCharacters - 1);
        if (boundary < Math.max(1, maxCharacters / 2)) {
            boundary = maxCharacters - 1;
        }
        return value.substring(0, boundary).stripTrailing() + "…";
    }

    public record BuiltPush(
            Map<String, Object> payload,
            String json,
            int bytes,
            int degradationStep,
            boolean rejected
    ) {
    }
}
