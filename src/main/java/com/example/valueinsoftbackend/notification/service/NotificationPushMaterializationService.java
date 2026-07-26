package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPushOutbox;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationPushMaterializationService {
    private final NotificationProperties properties;
    private final DbNotificationDevice devices;
    private final DeliveryKeyFactory keys;
    private final PushPayloadBuilder payloads;
    private final DbNotificationPushOutbox outbox;
    private final MeterRegistry meters;
    private final NotificationControlGate controls;

    public NotificationPushMaterializationService(NotificationProperties properties,
                                                  DbNotificationDevice devices,
                                                  DeliveryKeyFactory keys,
                                                  PushPayloadBuilder payloads,
                                                  DbNotificationPushOutbox outbox,
                                                  MeterRegistry meters,
                                                  NotificationControlGate controls) {
        this.properties = properties;
        this.devices = devices;
        this.keys = keys;
        this.payloads = payloads;
        this.outbox = outbox;
        this.meters = meters;
        this.controls = controls;
    }

    public int materialize(long companyId,
                           int userId,
                           long recipientId,
                           UUID recipientUuid,
                           int aggregateCount,
                           NotificationEvent event,
                           NotificationCatalogEntry catalog,
                           RenderedNotification rendered) {
        if (!catalog.defaultChannelPush()) {
            return 0;
        }
        DisabledAction channelAction = disabledAction(NotificationComponent.PUSH);
        if (channelAction == DisabledAction.SUPPRESS) {
            meters.counter("notification.push.suppressed",
                    "scope", "channel", "component", "PUSH").increment();
            return 0;
        }
        int created = 0;
        for (NotificationDevice device : devices.activeForUser(companyId, userId)) {
            NotificationComponent providerComponent = "fcm".equals(device.provider())
                    ? NotificationComponent.FCM : NotificationComponent.APNS;
            DisabledAction providerAction = disabledAction(providerComponent);
            if (providerAction == DisabledAction.SUPPRESS) {
                meters.counter("notification.push.suppressed",
                        "scope", "provider",
                        "component", providerComponent.key()).increment();
                continue;
            }
            String cancellationReason =
                    channelAction == DisabledAction.CANCEL
                            || providerAction == DisabledAction.CANCEL
                            ? "CONTROL_DISABLED" : null;
            int payloadVersion = Math.min(
                    properties.getPayload().getCurrentVersion(),
                    device.payloadVersionMax());
            byte[] deliveryKey = keys.create(
                    companyId, event.eventId(), userId,
                    device.deviceId(), "push", payloadVersion);
            PushPayloadBuilder.BuiltPush payload = payloads.build(
                    device.provider(), companyId, userId, recipientUuid, aggregateCount,
                    event, catalog, rendered, payloadVersion);
            if (outbox.reserveAndInsert(
                    deliveryKey, companyId, userId, recipientId, recipientUuid,
                    event, device, payload, payloadVersion,
                    properties.getDispatch().getMaxAttempts(),
                    cancellationReason)) {
                created++;
            } else {
                meters.counter("notification.dispatch.dedup_skip").increment();
            }
        }
        return created;
    }

    private DisabledAction disabledAction(NotificationComponent component) {
        if (controls.isEnabled(component)) {
            return DisabledAction.ENABLED;
        }
        return switch (controls.suppressionMode(component)) {
            case "QUEUE" -> DisabledAction.QUEUE;
            case "CANCEL" -> DisabledAction.CANCEL;
            default -> DisabledAction.SUPPRESS;
        };
    }

    private enum DisabledAction {
        ENABLED, SUPPRESS, QUEUE, CANCEL
    }
}
