package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationControlProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.provider.ProviderErrorClassifier;
import com.example.valueinsoftbackend.notification.provider.PushProviderResponse;
import com.example.valueinsoftbackend.notification.provider.PushProviderRouter;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class NotificationDispatchService {
    private final NotificationControlGate controls;
    private final DbNotificationDevice devices;
    private final PushProviderRouter providers;
    private final ProviderErrorClassifier classifier;
    private final NotificationDeliveryResultService results;

    public NotificationDispatchService(NotificationControlGate controls,
                                       DbNotificationDevice devices,
                                       PushProviderRouter providers,
                                       ProviderErrorClassifier classifier,
                                       NotificationDeliveryResultService results) {
        this.controls = controls;
        this.devices = devices;
        this.providers = providers;
        this.classifier = classifier;
        this.results = results;
    }

    public void dispatch(PushOutboxItem item) {
        if (item.createdAt().plusSeconds(item.ttlSeconds())
                .isBefore(OffsetDateTime.now())) {
            results.cancel(item, "STALE_ON_RESUME");
            return;
        }
        if (!controls.isEnabled(NotificationComponent.PUSH)) {
            suppress(item, NotificationComponent.PUSH, "CHANNEL_DISABLED");
            return;
        }
        NotificationComponent providerComponent = "fcm".equals(item.provider())
                ? NotificationComponent.FCM : NotificationComponent.APNS;
        if (!controls.isEnabled(providerComponent)) {
            suppress(item, providerComponent, "PROVIDER_DISABLED");
            return;
        }

        NotificationDevice device = devices.findById(item.deviceId()).orElse(null);
        if (device == null || !device.activeFor(
                item.companyId(), item.userId(), item.deviceBindingVersion())) {
            results.cancel(item, "DEVICE_BINDING_CHANGED");
            return;
        }

        PushProviderResponse response = providers.send(device, item);
        ProviderErrorClassifier.Decision decision =
                classifier.classify(item.provider(), response);
        if ("apns".equals(item.provider())
                && "InvalidProviderToken".equals(decision.errorCode())) {
            providers.tripCircuit("apns");
        }
        if (shouldRefreshCredential(item.provider(), decision)) {
            providers.invalidateCredentials(item.provider());
            response = providers.send(device, item);
            decision = classifier.classify(item.provider(), response);
        }
        results.complete(item, device, response, decision);
    }

    private void suppress(PushOutboxItem item,
                          NotificationComponent component,
                          String cancellationReason) {
        String mode = controls.suppressionMode(component);
        if ("QUEUE".equals(mode)) {
            results.requeue(item, cancellationReason, 60);
        } else {
            results.cancel(item,
                    "CANCEL".equals(mode) ? "CONTROL_DISABLED" : cancellationReason);
        }
    }

    private static boolean shouldRefreshCredential(
            String provider, ProviderErrorClassifier.Decision decision) {
        return ("fcm".equals(provider)
                && ("THIRD_PARTY_AUTH_ERROR".equals(decision.errorCode())
                    || "UNAUTHENTICATED".equals(decision.errorCode())))
                || ("apns".equals(provider)
                && "ExpiredProviderToken".equals(decision.errorCode()));
    }
}
