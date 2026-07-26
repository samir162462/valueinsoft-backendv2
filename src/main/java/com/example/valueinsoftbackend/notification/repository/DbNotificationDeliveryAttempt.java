package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.provider.ProviderErrorClassifier;
import com.example.valueinsoftbackend.notification.provider.PushProviderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbNotificationDeliveryAttempt {
    private final JdbcTemplate jdbc;

    public DbNotificationDeliveryAttempt(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(PushOutboxItem item,
                       PushProviderResponse response,
                       ProviderErrorClassifier.Decision decision) {
        Integer httpStatus = response == null || response.httpStatus() == 0
                ? null : response.httpStatus();
        int latencyMs = response == null ? 0
                : Math.toIntExact(Math.min(Integer.MAX_VALUE,
                Math.max(0, response.latency().toMillis())));
        jdbc.update("""
                INSERT INTO public.notification_delivery_attempt (
                    outbox_uuid, outbox_created_at, company_id, device_id,
                    provider, attempt_no, http_status, provider_message_id,
                    error_code, error_class, retry_after_seconds, apns_unique_id,
                    invalidation_at, payload_bytes, latency_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, item.outboxUuid(), item.createdAt(), item.companyId(), item.deviceId(),
                item.provider(), item.attemptCount(), httpStatus,
                decision.providerMessageId(), decision.errorCode(), decision.errorClass(),
                decision.retryAfterSeconds() == 0 ? null : decision.retryAfterSeconds(),
                decision.apnsUniqueId(), decision.invalidationAt(), item.payloadBytes(), latencyMs);
    }

    public void recordCancellation(PushOutboxItem item, String reason) {
        ProviderErrorClassifier.Decision decision =
                new ProviderErrorClassifier.Decision(
                        "cancelled", reason, false,
                        ProviderErrorClassifier.DeviceAction.NONE,
                        null, null, 0, null);
        record(item, new PushProviderResponse(
                0, "", java.util.Map.of(), java.time.Duration.ZERO, null), decision);
    }
}
