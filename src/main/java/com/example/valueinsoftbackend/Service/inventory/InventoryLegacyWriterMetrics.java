package com.example.valueinsoftbackend.Service.inventory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Phase 0 migration telemetry for inventory writers that must reach zero usage before retirement.
 * Tags are intentionally bounded: never add tenant, actor, product, serial, or request identifiers.
 */
@Component
public class InventoryLegacyWriterMetrics {

    private final MeterRegistry registry;

    public InventoryLegacyWriterMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void finish(Timer.Sample sample, String endpoint, String outcome) {
        Counter.builder("valueinsoft.inventory.legacy_writer.requests")
                .description("Calls to inventory writer endpoints scheduled for retirement")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(registry)
                .increment();

        sample.stop(Timer.builder("valueinsoft.inventory.legacy_writer.latency")
                .description("Latency of inventory writer endpoints scheduled for retirement")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry));
    }
}
