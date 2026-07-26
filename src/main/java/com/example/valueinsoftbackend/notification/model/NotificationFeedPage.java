package com.example.valueinsoftbackend.notification.model;

import java.util.List;

public record NotificationFeedPage(List<NotificationFeedItem> items, String nextCursor) {
}
