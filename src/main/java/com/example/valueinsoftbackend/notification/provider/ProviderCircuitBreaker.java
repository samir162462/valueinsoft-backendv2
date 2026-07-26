package com.example.valueinsoftbackend.notification.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProviderCircuitBreaker {
    private final int threshold;
    private final Duration openDuration;
    private final Clock clock;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();
    private volatile Instant openUntil;

    public ProviderCircuitBreaker(int threshold, Duration openDuration) {
        this(threshold, openDuration, Clock.systemUTC());
    }

    ProviderCircuitBreaker(int threshold, Duration openDuration, Clock clock) {
        this.threshold = Math.max(1, threshold);
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public boolean tryAcquire() {
        Instant until = openUntil;
        if (until == null) {
            return true;
        }
        if (clock.instant().isBefore(until)) {
            return false;
        }
        return halfOpenProbe.compareAndSet(false, true);
    }

    public void success() {
        failures.set(0);
        openUntil = null;
        halfOpenProbe.set(false);
    }

    public void failure() {
        halfOpenProbe.set(false);
        if (failures.incrementAndGet() >= threshold) {
            openUntil = clock.instant().plus(openDuration);
        }
    }

    public void forceOpen() {
        failures.set(threshold);
        halfOpenProbe.set(false);
        openUntil = clock.instant().plus(openDuration);
    }

    public String state() {
        Instant until = openUntil;
        if (until == null) {
            return "closed";
        }
        return clock.instant().isBefore(until) ? "open" : "half_open";
    }
}
