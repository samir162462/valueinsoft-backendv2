package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.provider.ProviderErrorClassifier;
import com.example.valueinsoftbackend.notification.provider.PushProviderResponse;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDeliveryAttempt;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPushOutbox;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationDeliveryResultServiceTest {
    private final DbNotificationPushOutbox outbox = mock(DbNotificationPushOutbox.class);
    private final DbNotificationDeliveryAttempt attempts =
            mock(DbNotificationDeliveryAttempt.class);
    private final DbNotificationDevice devices = mock(DbNotificationDevice.class);
    private final NotificationDeliveryResultService results =
            new NotificationDeliveryResultService(
                    outbox, attempts, devices,
                    new NotificationBackoffPolicy(new NotificationProperties()),
                    new SimpleMeterRegistry());

    @Test
    void apnsUnregisteredAtOrBeforeLastRotationDoesNotInvalidateCurrentToken() {
        OffsetDateTime rotatedAt = OffsetDateTime.now();
        NotificationDevice device = device(rotatedAt);
        PushOutboxItem item = item();
        var decision = decision(rotatedAt.minusSeconds(1));

        results.complete(item, device, response(), decision);

        verify(outbox).markFailed(
                item, true, "Unregistered", "{\"reason\":\"Unregistered\"}", 0);
        verify(devices, never()).invalidate(
                anyLong(), anyString(), any(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    void apnsUnregisteredAfterLastRotationStalesDevice() {
        OffsetDateTime rotatedAt = OffsetDateTime.now();
        NotificationDevice device = device(rotatedAt);
        PushOutboxItem item = item();
        OffsetDateTime invalidatedAt = rotatedAt.plusSeconds(1);
        var decision = decision(invalidatedAt);

        results.complete(item, device, response(), decision);

        verify(devices).invalidate(
                device.deviceId(), "Unregistered", invalidatedAt,
                false, true, device.userId());
    }

    @Test
    void everyResultWriteEntryPointRequiresNewTransaction() throws Exception {
        assertRequiresNew("complete", PushOutboxItem.class, NotificationDevice.class,
                PushProviderResponse.class, ProviderErrorClassifier.Decision.class);
        assertRequiresNew("cancel", PushOutboxItem.class, String.class);
        assertRequiresNew("requeue", PushOutboxItem.class, String.class, int.class);
    }

    private static void assertRequiresNew(String name, Class<?>... types) throws Exception {
        Method method = NotificationDeliveryResultService.class.getMethod(name, types);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static ProviderErrorClassifier.Decision decision(OffsetDateTime at) {
        return new ProviderErrorClassifier.Decision(
                "permanent", "Unregistered", false,
                ProviderErrorClassifier.DeviceAction.STALE,
                at, null, 0, null);
    }

    private static PushProviderResponse response() {
        return new PushProviderResponse(
                410, "{\"reason\":\"Unregistered\"}", Map.of(),
                Duration.ofMillis(3), null);
    }

    private static NotificationDevice device(OffsetDateTime rotatedAt) {
        return new NotificationDevice(
                8, UUID.randomUUID(), 12, 13, null, "install",
                "apns", "com.valueinsoft", "production", "ios",
                2, new byte[]{1}, "k1", new byte[32],
                "en", "UTC", 1, "active", 0, null, rotatedAt);
    }

    private static PushOutboxItem item() {
        return new PushOutboxItem(
                OffsetDateTime.now(), 1, UUID.randomUUID(), new byte[32],
                13, 14, 15, UUID.randomUUID(), 12, 8, 2,
                "apns", "normal", "{}", 1, 2, "collapse",
                3_600, "claimed", 1, 6);
    }
}
