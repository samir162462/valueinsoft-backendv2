package com.example.valueinsoftbackend.fx.model;

import java.time.OffsetDateTime;

public record FxDeepSeekFetchResult(
        FxRatePayload payload,
        String rawResponse,
        OffsetDateTime requestTimestamp,
        OffsetDateTime responseTimestamp
) {
}
