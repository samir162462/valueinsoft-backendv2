package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushPayloadBuilderTest {
    private final NotificationProperties properties = new NotificationProperties();
    private final ObjectMapper mapper = new ObjectMapper();
    private final PushPayloadBuilder builder = new PushPayloadBuilder(
            properties, mapper, new SimpleMeterRegistry());

    @Test
    void payloadsContainOnlySafeRoutingDataAndNeverAppleCriticalAlertFlag()
            throws Exception {
        properties.getPayload().setMaxBytes(3_800);

        for (String provider : Set.of("fcm", "apns")) {
            var built = build(provider, "Safe preview");
            assertThat(built.bytes())
                    .isEqualTo(built.json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            assertThat(built.bytes()).isLessThanOrEqualTo(3_800);
            assertThat(built.json())
                    .doesNotContain("rendered_body", "secret-param", "\"critical\":1");
            assertThat(mapper.readTree(built.json()).isObject()).isTrue();
        }
    }

    @Test
    void exercisesAllSixDegradationStepsAndRejectsOnlyAfterPreviewIsDropped() {
        Set<Integer> observed = new HashSet<>();
        String preview = ("word ".repeat(1_200)).trim();
        for (int ceiling = 1; ceiling <= 1_200; ceiling++) {
            properties.getPayload().setMaxBytes(ceiling);
            observed.add(build("apns", preview).degradationStep());
        }

        assertThat(observed).contains(1, 2, 3, 4, 5, 6);
        properties.getPayload().setMaxBytes(1);
        var rejected = build("fcm", preview);
        assertThat(rejected.rejected()).isTrue();
        assertThat(rejected.degradationStep()).isEqualTo(6);
    }

    private PushPayloadBuilder.BuiltPush build(String provider, String preview) {
        NotificationEvent event = new NotificationEvent(
                10L, "inventory.stock.low", 2, 3,
                "product", 9L, Map.of("secret", "secret-param"),
                "critical", "stock:9", "test", null, "corr", 180, Instant.now());
        NotificationCatalogEntry catalog = new NotificationCatalogEntry(
                event.typeKey(), "inventory", "normal", "allowed",
                null, 600, "/inventory/{subjectId}", null,
                180, 300, true,
                // Phase 5 additions: defaultChannelInApp, userMutable,
                // bypassesQuietHours, producerRateLimitPerMin.
                true, true, false, null);
        RenderedNotification rendered = new RenderedNotification(
                "Low stock", "Sensitive rendered body", preview, "en",
                1, "rendered", "/inventory/9", "stock:9",
                "Inventory update");
        return builder.build(
                provider, 44, 55, UUID.fromString(
                        "00000000-0000-0000-0000-000000000123"),
                4, event, catalog, rendered, 1);
    }
}
