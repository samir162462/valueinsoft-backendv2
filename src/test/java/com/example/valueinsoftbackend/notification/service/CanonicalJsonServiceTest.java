package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonServiceTest {
    private final CanonicalJsonService canonical = new CanonicalJsonService(new ObjectMapper());
    private final NotificationIdempotencyService idempotency =
            new NotificationIdempotencyService(canonical);

    @Test
    void ordersKeysRecursivelyAndNormalizesNumbers() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("x", new BigDecimal("2.00"));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("z", java.util.List.of(3.0, 2));
        value.put("b", nested);
        value.put("a", new BigDecimal("1.000"));

        assertThat(canonical.canonicalize(value))
                .isEqualTo("{\"a\":1,\"b\":{\"x\":2},\"z\":[3,2]}");
    }

    @Test
    void equivalentRequestsHaveSameFingerprint() {
        NotificationRequest first = NotificationRequest.builder(
                        9, "inventory.stock.low", "stock:42")
                .params(Map.of("qty", new BigDecimal("3.00"), "product", "Phone"))
                .build();
        NotificationRequest second = NotificationRequest.builder(
                        9, "inventory.stock.low", "stock:42")
                .params(Map.of("product", "Phone", "qty", 3))
                .build();

        assertThat(idempotency.fingerprint(first))
                .containsExactly(idempotency.fingerprint(second));
    }
}
