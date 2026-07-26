package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.provider.PushProviderRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class NotificationCredentialProbe implements NotificationWorkerTask {
    private final PushProviderRouter providers;

    public NotificationCredentialProbe(PushProviderRouter providers) {
        this.providers = providers;
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.DISPATCH;
    }

    @Override
    public String workerName() {
        return "CREDENTIAL_PROBE";
    }

    @Override
    public Duration delay() {
        return Duration.ofDays(1);
    }

    @Override
    public Duration initialDelay() {
        return Duration.ofMinutes(1);
    }

    @Override
    public void runCycle() {
        providers.providers().forEach((name, provider) -> {
            var result = provider.probeCredentials();
            if (!result.healthy()) {
                log.error("Notification provider credential probe failed: provider={}, detail={}",
                        name, result.detail());
            }
        });
    }
}
