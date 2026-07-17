package com.example.valueinsoftbackend.Service.openitems;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Phase 8 operational metrics for the AR/AP open-items write and reconciliation paths. */
@Component
public class OpenItemsMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<DriftKey, AtomicReference<Double>> driftGauges = new ConcurrentHashMap<>();

    public OpenItemsMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startAllocation() {
        return Timer.start(registry);
    }

    public void finishAllocation(Timer.Sample sample, String side, String source, String outcome) {
        sample.stop(Timer.builder("valueinsoft.openitems.allocation.latency")
                .description("AR/AP allocation latency")
                .tag("side", side)
                .tag("source", source)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry));
    }

    public void recordIdempotencyReplay(String side, String source) {
        Counter.builder("valueinsoft.openitems.idempotency.replays")
                .description("Allocation requests served by idempotent replay")
                .tag("side", side)
                .tag("source", source)
                .register(registry)
                .increment();
    }

    public void recordTriggerRejection(String side, String source) {
        Counter.builder("valueinsoft.openitems.trigger.rejections")
                .description("Database integrity rejections from an open-items allocation path")
                .tag("side", side)
                .tag("source", source)
                .register(registry)
                .increment();
    }

    public void updateReconciliationDrift(int companyId, String side, BigDecimal variance) {
        DriftKey key = new DriftKey(companyId, side);
        AtomicReference<Double> value = driftGauges.computeIfAbsent(key, ignored -> {
            AtomicReference<Double> reference = new AtomicReference<>(0D);
            Gauge.builder("valueinsoft.openitems.reconciliation.drift", reference, AtomicReference::get)
                    .description("Absolute AR/AP subledger-to-control variance for a tenant")
                    .tag("company_id", Integer.toString(companyId))
                    .tag("side", side)
                    .strongReference(true)
                    .register(registry);
            return reference;
        });
        value.set(variance == null ? 0D : variance.abs().doubleValue());
    }

    private record DriftKey(int companyId, String side) {
    }
}
