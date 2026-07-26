package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;

public record NotificationSummary(
        long unseenCount,
        long unreadCount,
        Instant lastEventAt,
        long changeSequence,
        long companyId
) {
}
