package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.Decision;
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
    private final NotificationPreferenceService preferences;

    public NotificationPushMaterializationService(NotificationProperties properties,
                                                  DbNotificationDevice devices,
                                                  DeliveryKeyFactory keys,
                                                  PushPayloadBuilder payloads,
                                                  DbNotificationPushOutbox outbox,
                                                  MeterRegistry meters,
                                                  NotificationControlGate controls,
                                                  NotificationPreferenceService preferences) {
        this.properties = properties;
        this.devices = devices;
        this.keys = keys;
        this.payloads = payloads;
        this.outbox = outbox;
        this.meters = meters;
        this.controls = controls;
        this.preferences = preferences;
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

        /*
         * User preferences (NC-5.5). Evaluated once per recipient, before any device loop,
         * because quiet hours, DND and min-priority apply to the person rather than to a
         * particular handset.
         *
         * A suppressed push still writes an outbox row, marked `cancelled` with the reason
         * the user actually set — QUIET_HOURS, DND, PREFERENCE_MUTED or MIN_PRIORITY. That
         * costs one row and buys an auditable answer to "why didn't I get notified?", which
         * is otherwise unanswerable. The in-app recipient row is untouched: turning push off
         * must never lose feed history (invariant B-15), and the v1 rule is suppress, not
         * defer (§6.8).
         */
        Decision decision = preferences.decide(companyId, userId, catalog);
        String preferenceCancellation = decision.pushAllowed()
                ? null : decision.pushReason().code();
        if (preferenceCancellation != null) {
            meters.counter("notification.push.suppressed",
                    "scope", "preference", "component", preferenceCancellation).increment();
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
            // The user's own preference wins over an operator CANCEL: if someone muted a
            // type, "PREFERENCE_MUTED" is the truthful reason to record, not a control-plane
            // code that would send support looking in the wrong place.
            String cancellationReason = preferenceCancellation != null
                    ? preferenceCancellation
                    : (channelAction == DisabledAction.CANCEL
                            || providerAction == DisabledAction.CANCEL
                            ? "CONTROL_DISABLED" : null);
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
