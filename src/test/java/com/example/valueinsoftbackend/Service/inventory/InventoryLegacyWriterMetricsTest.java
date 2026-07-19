package com.example.valueinsoftbackend.Service.inventory;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InventoryLegacyWriterMetricsTest {

    @Test
    void recordsBoundedEndpointAndOutcomeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InventoryLegacyWriterMetrics metrics = new InventoryLegacyWriterMetrics(registry);
        Timer.Sample sample = metrics.start();

        metrics.finish(sample, "generic_transaction", "success");

        assertEquals(1D, registry.get("valueinsoft.inventory.legacy_writer.requests")
                .tag("endpoint", "generic_transaction")
                .tag("outcome", "success")
                .counter()
                .count());
        assertNotNull(registry.get("valueinsoft.inventory.legacy_writer.latency")
                .tag("endpoint", "generic_transaction")
                .tag("outcome", "success")
                .timer());
    }
}
