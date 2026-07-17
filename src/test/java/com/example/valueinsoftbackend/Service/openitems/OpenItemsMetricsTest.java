package com.example.valueinsoftbackend.Service.openitems;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenItemsMetricsTest {

    @Test
    void exposesCountersTimerAndTenantDriftGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OpenItemsMetrics metrics = new OpenItemsMetrics(registry);

        var sample = metrics.startAllocation();
        metrics.recordIdempotencyReplay("AR", "receipt");
        metrics.recordTriggerRejection("AR", "receipt");
        metrics.finishAllocation(sample, "AR", "receipt", "replay");
        metrics.updateReconciliationDrift(7, "AR", new BigDecimal("-12.50"));

        assertEquals(1D, registry.get("valueinsoft.openitems.idempotency.replays").counter().count());
        assertEquals(1D, registry.get("valueinsoft.openitems.trigger.rejections").counter().count());
        assertEquals(1L, registry.get("valueinsoft.openitems.allocation.latency").timer().count());
        assertEquals(12.5D, registry.get("valueinsoft.openitems.reconciliation.drift")
                .tag("company_id", "7").tag("side", "AR").gauge().value());
    }

    @Test
    void aspectClassifiesReplayAndDatabaseRejection() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OpenItemsMetricsAspect aspect = new OpenItemsMetricsAspect(new OpenItemsMetrics(registry));
        org.aspectj.lang.ProceedingJoinPoint replay = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(replay.proceed()).thenReturn(new com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels.AllocationResult(
                1, BigDecimal.ONE, BigDecimal.ZERO, List.of(), true));

        aspect.arReceipt(replay);

        org.aspectj.lang.ProceedingJoinPoint rejected = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(rejected.proceed()).thenThrow(new org.springframework.dao.DataIntegrityViolationException("guard"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> aspect.apReceipt(rejected));
        assertEquals(1D, registry.get("valueinsoft.openitems.idempotency.replays")
                .tag("side", "AR").counter().count());
        assertEquals(1D, registry.get("valueinsoft.openitems.trigger.rejections")
                .tag("side", "AP").counter().count());
    }
}
