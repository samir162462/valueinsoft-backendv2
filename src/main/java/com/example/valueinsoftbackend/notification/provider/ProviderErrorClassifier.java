package com.example.valueinsoftbackend.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class ProviderErrorClassifier {
    private final ObjectMapper objectMapper;

    public ProviderErrorClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Decision classify(String provider, PushProviderResponse response) {
        if (response.transportError() != null) {
            return new Decision(
                    "transport", "TRANSPORT_ERROR", true,
                    DeviceAction.NONE, null, null, retryAfter(response), null);
        }
        return switch (provider) {
            case "fcm" -> classifyFcm(response);
            case "apns" -> classifyApns(response);
            default -> new Decision(
                    "permanent", "UNSUPPORTED_PROVIDER", false,
                    DeviceAction.NONE, null, null, 0, null);
        };
    }

    private Decision classifyFcm(PushProviderResponse response) {
        if (response.httpStatus() >= 200 && response.httpStatus() < 300) {
            return success(messageId(response.body(), "name"));
        }
        JsonNode body = json(response.body());
        String status = fcmErrorCode(body);
        String upperBody = response.body() == null
                ? "" : response.body().toUpperCase(Locale.ROOT);
        String code = status == null || status.isBlank()
                ? "HTTP_" + response.httpStatus() : status;
        return switch (code) {
            case "UNREGISTERED" -> permanent(code, DeviceAction.STALE);
            case "SENDER_ID_MISMATCH" -> permanent(code, DeviceAction.REVOKE);
            case "INVALID_ARGUMENT" -> fcmTokenFieldViolation(body)
                    || upperBody.contains("MESSAGE.TOKEN")
                    ? permanent(code, DeviceAction.REVOKE)
                    : permanent("PAYLOAD_INVALID_ARGUMENT", DeviceAction.NONE);
            case "QUOTA_EXCEEDED", "UNAVAILABLE", "INTERNAL" ->
                    retryable(code, retryAfter(response));
            case "THIRD_PARTY_AUTH_ERROR", "UNAUTHENTICATED" ->
                    retryable(code, retryAfter(response));
            default -> response.httpStatus() == 429 || response.httpStatus() >= 500
                    ? retryable(code, retryAfter(response))
                    : permanent(code, DeviceAction.NONE);
        };
    }

    private Decision classifyApns(PushProviderResponse response) {
        if (response.httpStatus() >= 200 && response.httpStatus() < 300) {
            String messageId = response.firstHeader("apns-id");
            return success(messageId);
        }
        String reason = jsonText(response.body(), "/reason");
        String code = reason == null || reason.isBlank()
                ? "HTTP_" + response.httpStatus() : reason;
        OffsetDateTime invalidation = null;
        JsonNode timestamp = json(response.body()).path("timestamp");
        if (timestamp.canConvertToLong()) {
            long raw = timestamp.asLong();
            invalidation = OffsetDateTime.ofInstant(
                    raw > 10_000_000_000L
                            ? Instant.ofEpochMilli(raw)
                            : Instant.ofEpochSecond(raw),
                    ZoneOffset.UTC);
        }
        return switch (code) {
            case "Unregistered" -> new Decision(
                    "permanent", code, false, DeviceAction.STALE,
                    invalidation, null, 0, response.firstHeader("apns-unique-id"));
            case "BadDeviceToken" -> permanent(code, DeviceAction.REVOKE);
            case "BadCollapseId", "PayloadTooLarge" ->
                    permanent(code, DeviceAction.NONE);
            case "ExpiredProviderToken", "TooManyRequests",
                 "TooManyProviderTokenUpdates" ->
                    retryable(code, retryAfter(response));
            case "InvalidProviderToken" ->
                    retryable(code, retryAfter(response));
            default -> response.httpStatus() == 429 || response.httpStatus() >= 500
                    ? retryable(code, retryAfter(response))
                    : permanent(code, DeviceAction.NONE);
        };
    }

    private Decision success(String messageId) {
        return new Decision(
                "success", null, false, DeviceAction.RESET,
                null, messageId, 0, null);
    }

    private Decision permanent(String code, DeviceAction action) {
        return new Decision(
                "permanent", code, false, action,
                null, null, 0, null);
    }

    private Decision retryable(String code, int retryAfter) {
        return new Decision(
                "retryable", code, true, DeviceAction.NONE,
                null, null, retryAfter, null);
    }

    private int retryAfter(PushProviderResponse response) {
        String value = response.firstHeader("Retry-After");
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime retryAt = ZonedDateTime.parse(
                        value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(0, (int) java.time.Duration.between(
                        Instant.now(), retryAt.toInstant()).toSeconds());
            } catch (RuntimeException invalidDate) {
                return 0;
            }
        }
    }

    private String messageId(String body, String field) {
        return jsonText(body, "/" + field);
    }

    private String fcmErrorCode(JsonNode body) {
        JsonNode details = body.at("/error/details");
        if (details.isArray()) {
            for (JsonNode detail : details) {
                String code = detail.path("errorCode").asText();
                if (!code.isBlank()) {
                    return code;
                }
            }
        }
        return body.at("/error/status").asText();
    }

    private boolean fcmTokenFieldViolation(JsonNode body) {
        JsonNode details = body.at("/error/details");
        if (!details.isArray()) {
            return false;
        }
        for (JsonNode detail : details) {
            JsonNode violations = detail.path("fieldViolations");
            if (violations.isArray()) {
                for (JsonNode violation : violations) {
                    if ("message.token".equals(violation.path("field").asText())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String jsonText(String body, String pointer) {
        JsonNode node = json(body).at(pointer);
        return node.isTextual() ? node.asText() : null;
    }

    private JsonNode json(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    public enum DeviceAction {
        NONE, RESET, STALE, REVOKE
    }

    public record Decision(
            String errorClass,
            String errorCode,
            boolean retryable,
            DeviceAction deviceAction,
            OffsetDateTime invalidationAt,
            String providerMessageId,
            int retryAfterSeconds,
            String apnsUniqueId
    ) {
    }
}
