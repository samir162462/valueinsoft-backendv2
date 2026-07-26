package com.example.valueinsoftbackend.notification.provider;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record PushProviderResponse(
        int httpStatus,
        String body,
        Map<String, List<String>> headers,
        Duration latency,
        Throwable transportError
) {
    public static PushProviderResponse transport(Throwable error, Duration latency) {
        return new PushProviderResponse(0, "", Map.of(), latency, error);
    }

    public String firstHeader(String name) {
        if (headers == null) {
            return null;
        }
        for (var entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return null;
    }
}
