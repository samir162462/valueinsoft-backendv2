package com.example.valueinsoftbackend.notification.provider;

import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;

public record PushSendRequest(
        String credential,
        NotificationDevice device,
        PushOutboxItem outbox
) {
}
