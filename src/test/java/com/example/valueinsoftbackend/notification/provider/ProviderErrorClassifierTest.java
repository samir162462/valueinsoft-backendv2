package com.example.valueinsoftbackend.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderErrorClassifierTest {
    private final ProviderErrorClassifier classifier =
            new ProviderErrorClassifier(new ObjectMapper());

    @Test
    void classifiesFcmDocumentedCodesIncludingNestedErrorCode() {
        assertThat(fcm(404, "UNREGISTERED").deviceAction())
                .isEqualTo(ProviderErrorClassifier.DeviceAction.STALE);
        assertThat(fcm(403, "SENDER_ID_MISMATCH").deviceAction())
                .isEqualTo(ProviderErrorClassifier.DeviceAction.REVOKE);
        assertThat(fcm(429, "QUOTA_EXCEEDED").retryable()).isTrue();
        assertThat(fcm(503, "UNAVAILABLE").retryable()).isTrue();
        assertThat(fcm(500, "INTERNAL").retryable()).isTrue();
        assertThat(fcm(401, "THIRD_PARTY_AUTH_ERROR").retryable()).isTrue();
    }

    @Test
    void distinguishesTokenFromPayloadInvalidArgument() {
        String tokenViolation = """
                {"error":{"status":"INVALID_ARGUMENT","details":[{
                  "fieldViolations":[{"field":"message.token"}]}]}}
                """;
        var token = classifier.classify("fcm", response(400, tokenViolation, Map.of()));
        var payload = classifier.classify(
                "fcm", response(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}", Map.of()));

        assertThat(token.deviceAction())
                .isEqualTo(ProviderErrorClassifier.DeviceAction.REVOKE);
        assertThat(payload.errorCode()).isEqualTo("PAYLOAD_INVALID_ARGUMENT");
        assertThat(payload.deviceAction())
                .isEqualTo(ProviderErrorClassifier.DeviceAction.NONE);
    }

    @Test
    void classifiesApnsCodesAndParsesMillisecondInvalidationTimestamp() {
        long milliseconds = Instant.parse("2026-07-25T10:15:30Z").toEpochMilli();
        var unregistered = classifier.classify("apns", response(
                410, "{\"reason\":\"Unregistered\",\"timestamp\":" + milliseconds + "}",
                Map.of("apns-unique-id", List.of("unique-1"))));

        assertThat(unregistered.deviceAction())
                .isEqualTo(ProviderErrorClassifier.DeviceAction.STALE);
        assertThat(unregistered.invalidationAt().toInstant())
                .isEqualTo(Instant.ofEpochMilli(milliseconds));
        assertThat(apns(400, "BadDeviceToken").deviceAction())
                .isEqualTo(ProviderErrorClassifier.DeviceAction.REVOKE);
        assertThat(apns(400, "BadCollapseId").retryable()).isFalse();
        assertThat(apns(400, "PayloadTooLarge").retryable()).isFalse();
        assertThat(apns(403, "ExpiredProviderToken").retryable()).isTrue();
        assertThat(apns(403, "InvalidProviderToken").retryable()).isTrue();
        assertThat(apns(429, "TooManyRequests").retryable()).isTrue();
        assertThat(apns(429, "TooManyProviderTokenUpdates").retryable()).isTrue();
    }

    @Test
    void parsesDeltaAndHttpDateRetryAfter() {
        var delta = classifier.classify("fcm", response(
                429, fcmBody("QUOTA_EXCEEDED"), Map.of("Retry-After", List.of("180"))));
        String date = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                .plusSeconds(120)
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        var httpDate = classifier.classify("apns", response(
                429, "{\"reason\":\"TooManyRequests\"}",
                Map.of("Retry-After", List.of(date))));

        assertThat(delta.retryAfterSeconds()).isEqualTo(180);
        assertThat(httpDate.retryAfterSeconds()).isBetween(115, 120);
    }

    private ProviderErrorClassifier.Decision fcm(int status, String code) {
        return classifier.classify(
                "fcm", response(status, fcmBody(code), Map.of()));
    }

    private ProviderErrorClassifier.Decision apns(int status, String code) {
        return classifier.classify("apns", response(
                status, "{\"reason\":\"" + code + "\"}", Map.of()));
    }

    private static String fcmBody(String code) {
        return "{\"error\":{\"status\":\"FAILED\",\"details\":[{\"errorCode\":\""
                + code + "\"}]}}";
    }

    private static PushProviderResponse response(
            int status, String body, Map<String, List<String>> headers) {
        return new PushProviderResponse(status, body, headers, Duration.ofMillis(5), null);
    }
}
