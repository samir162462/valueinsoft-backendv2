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
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName()
            + ":" + UUID.randomUUID();

    public NotificationFanOutWorker(NotificationFanOutService service,
                                    NotificationProperties properties) {
        this.service = service;
        this.properties = properties;
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
        int remaining = properties.getFanOut().getClaimBatchSize();
        for (long companyId : service.tenantIds()) {
            while (remaining > 0) {
                var claimed = service.claimAndDecide(companyId, workerId);
                if (claimed.isEmpty()) {
                    break;
                }
                service.materialize(claimed.get());
                remaining--;
            }
            if (remaining == 0) {
                return;
            }
        }
    }
}
