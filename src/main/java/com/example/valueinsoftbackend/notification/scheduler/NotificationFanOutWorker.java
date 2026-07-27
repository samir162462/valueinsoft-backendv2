package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.service.NotificationFanOutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationFanOutWorker implements NotificationWorkerTask {
    private final NotificationFanOutService service;
    private final NotificationProperties properties;
    private final NotificationWorkSignal workSignal;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName()
            + ":" + UUID.randomUUID();

    public NotificationFanOutWorker(NotificationFanOutService service,
                                    NotificationProperties properties,
                                    NotificationWorkSignal workSignal) {
        this.service = service;
        this.properties = properties;
        this.workSignal = workSignal;
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.FANOUT;
    }

    @Override
    public Duration delay() {
        return Duration.ofMillis(properties.getFanOut().getPollDelayMs());
    }

    @Override
    public void runCycle() {
        runEventDrivenCycle();
    }

    @Override
    public boolean eventDriven() {
        return true;
    }

    @Override
    public boolean runEventDrivenCycle() {
        int remaining = properties.getFanOut().getClaimBatchSize();
        int processed = 0;
        for (long companyId : service.tenantIds()) {
            while (remaining > 0) {
                var claimed = service.claimAndDecide(companyId, workerId);
                if (claimed.isEmpty()) {
                    break;
                }
                service.materialize(claimed.get());
                workSignal.signal(NotificationComponent.DISPATCH);
                remaining--;
                processed++;
            }
            if (remaining == 0) {
                return true;
            }
        }
        return processed == properties.getFanOut().getClaimBatchSize();
    }
}
