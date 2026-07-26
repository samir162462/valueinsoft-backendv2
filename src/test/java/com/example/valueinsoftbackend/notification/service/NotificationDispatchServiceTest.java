package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.provider.ProviderErrorClassifier;
import com.example.valueinsoftbackend.notification.provider.PushProviderResponse;
import com.example.valueinsoftbackend.notification.provider.PushProviderRouter;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatchServiceTest {
    private final NotificationControlGate controls = mock(NotificationControlGate.class);
    private final DbNotificationDevice devices = mock(DbNotificationDevice.class);
    private final PushProviderRouter providers = mock(PushProviderRouter.class);
    private final NotificationDeliveryResultService results =
            mock(NotificationDeliveryResultService.class);
    private final NotificationDispatchService dispatch = new NotificationDispatchService(
            controls, devices, providers,
            new ProviderErrorClassifier(new ObjectMapper()), results);

    @BeforeEach
    void enabled() {
        when(controls.isEnabled(NotificationComponent.PUSH)).thenReturn(true);
        when(controls.isEnabled(NotificationComponent.FCM)).thenReturn(true);
    }

    @Test
    void bindingMismatchCancelsAndMakesZeroProviderCalls() {
        PushOutboxItem item = item();
        NotificationDevice mismatched = device(999);
        when(devices.findById(item.deviceId())).thenReturn(Optional.of(mismatched));

        dispatch.dispatch(item);

        verify(providers, never()).send(any(), any());
        verify(results).cancel(item, "DEVICE_BINDING_CHANGED");
    }

    @Test
    void unchangedBindingCallsProviderAndPersistsResult() {
        PushOutboxItem item = item();
        NotificationDevice matching = device(item.deviceBindingVersion());
        PushProviderResponse accepted = new PushProviderResponse(
                200, "{\"name\":\"projects/p/messages/1\"}", Map.of(),
                Duration.ofMillis(4), null);
        when(devices.findById(item.deviceId())).thenReturn(Optional.of(matching));
        when(providers.send(matching, item)).thenReturn(accepted);

        dispatch.dispatch(item);

        verify(providers).send(matching, item);
        verify(results).complete(
                org.mockito.ArgumentMatchers.eq(item),
                org.mockito.ArgumentMatchers.eq(matching),
                org.mockito.ArgumentMatchers.eq(accepted),
                any(ProviderErrorClassifier.Decision.class));
    }

    @Test
    void queueSuppressionRequeuesWithoutProviderCall() {
        PushOutboxItem item = item();
        when(controls.isEnabled(NotificationComponent.PUSH)).thenReturn(false);
        when(controls.suppressionMode(NotificationComponent.PUSH)).thenReturn("QUEUE");

        dispatch.dispatch(item);

        verify(providers, never()).send(any(), any());
        verify(results).requeue(item, "CHANNEL_DISABLED", 60);
    }

    private static PushOutboxItem item() {
        return new PushOutboxItem(
                OffsetDateTime.now(), 1L, UUID.randomUUID(), new byte[32],
                44L, 10L, 20L, UUID.randomUUID(), 55, 66L, 7L,
                "fcm", "normal", "{}", 1, 2, "collapse", 86_400,
                "claimed", 1, 6);
    }

    private static NotificationDevice device(long bindingVersion) {
        return new NotificationDevice(
                66L, UUID.randomUUID(), 55, 44L, 2, "install",
                "fcm", "com.valueinsoft", "none", "android",
                bindingVersion, new byte[]{1}, "k1", new byte[32],
                "en", "UTC", 1, "active", 0, null, OffsetDateTime.now());
    }
}
