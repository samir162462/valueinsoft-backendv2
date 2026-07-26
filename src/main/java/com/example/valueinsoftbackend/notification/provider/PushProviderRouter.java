package com.example.valueinsoftbackend.notification.provider;

import com.example.valueinsoftbackend.notification.config.ApnsProperties;
import com.example.valueinsoftbackend.notification.config.FcmProperties;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.PushOutboxItem;
import com.example.valueinsoftbackend.notification.service.NotificationTokenCipher;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PushProviderRouter {
    private final NotificationTokenCipher cipher;
    private final Map<String, PushProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, ProviderCircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final Phaser inflight = new Phaser(1);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final int shutdownDrainSeconds;

    public PushProviderRouter(NotificationTokenCipher cipher,
                              ObjectProvider<PushProvider> providerObjects,
                              FcmProperties fcm,
                              ApnsProperties apns,
                              MeterRegistry meters) {
        this.cipher = cipher;
        providerObjects.orderedStream()
                .forEach(provider -> providers.put(provider.provider(), provider));
        breakers.put("fcm", new ProviderCircuitBreaker(
                fcm.getCircuitBreakerFailureThreshold(),
                Duration.ofSeconds(fcm.getCircuitBreakerOpenSeconds())));
        breakers.put("apns", new ProviderCircuitBreaker(
                apns.getCircuitBreakerFailureThreshold(),
                Duration.ofSeconds(apns.getCircuitBreakerOpenSeconds())));
        this.shutdownDrainSeconds = apns.getShutdownDrainSeconds();
        for (String provider : breakers.keySet()) {
            Gauge.builder("notification.push.circuit_state",
                            breakers.get(provider),
                            breaker -> switch (breaker.state()) {
                                case "closed" -> 0;
                                case "half_open" -> 1;
                                default -> 2;
                            })
                    .tag("provider", provider)
                    .register(meters);
        }
        Gauge.builder("notification.push.inflight", inflight,
                        value -> Math.max(0, value.getRegisteredParties() - 1))
                .register(meters);
    }

    /**
     * The only push path that decrypts a device credential. Plaintext remains local to this
     * method and is never attached to a DTO, exception or metric.
     */
    public PushProviderResponse send(NotificationDevice device, PushOutboxItem outbox) {
        if (!accepting.get()) {
            return PushProviderResponse.transport(
                    new IllegalStateException("Push provider router is shutting down"),
                    Duration.ZERO);
        }
        PushProvider provider = providers.get(device.provider());
        ProviderCircuitBreaker breaker = breakers.get(device.provider());
        if (provider == null || breaker == null) {
            return PushProviderResponse.transport(
                    new IllegalStateException("Push provider is unavailable"), Duration.ZERO);
        }
        if (!breaker.tryAcquire()) {
            return new PushProviderResponse(
                    503, "{\"reason\":\"CIRCUIT_OPEN\"}", Map.of(),
                    Duration.ZERO, null);
        }
        inflight.register();
        String plaintext = null;
        try {
            plaintext = cipher.decrypt(
                    device.encryptedCredential(), device.encryptionKeyId());
            PushProviderResponse response =
                    provider.send(new PushSendRequest(plaintext, device, outbox));
            if (response.transportError() != null || response.httpStatus() == 429
                    || response.httpStatus() >= 500) {
                breaker.failure();
            } else {
                breaker.success();
            }
            return response;
        } finally {
            plaintext = null;
            inflight.arriveAndDeregister();
        }
    }

    public void invalidateCredentials(String provider) {
        PushProvider implementation = providers.get(provider);
        if (implementation != null) {
            implementation.invalidateCredentials();
        }
    }

    public void tripCircuit(String provider) {
        ProviderCircuitBreaker breaker = breakers.get(provider);
        if (breaker != null) {
            breaker.forceOpen();
        }
    }

    public Map<String, PushProvider> providers() {
        return Map.copyOf(providers);
    }

    @PreDestroy
    public void drain() {
        accepting.set(false);
        int phase = inflight.arrive();
        try {
            inflight.awaitAdvanceInterruptibly(
                    phase, shutdownDrainSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.TimeoutException ignored) {
            // Claimed rows are recovered by the stuck-claim reaper.
        } finally {
            providers.values().forEach(PushProvider::close);
        }
    }
}
