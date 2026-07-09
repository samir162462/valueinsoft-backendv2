package com.example.valueinsoftbackend.SecurityPack;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P1-5: brute-force / credential-stuffing protection for the authentication endpoint.
 *
 * <p>Tracks failed login attempts per username AND per client IP in memory. After
 * {@code maxAttempts} failures inside a rolling {@code window}, the key is locked for
 * {@code lockout}. A successful login resets both counters.</p>
 *
 * <p>In-memory means per-instance: adequate for single-instance / low-instance deployments.
 * For horizontal scaling, back this with Redis (already a project dependency) — the public
 * API here ({@link #assertNotLocked}, {@link #recordFailure}, {@link #reset}) stays the same.</p>
 *
 * <p>Set {@code vls.security.login.rate-limit.enforce=false} to run in log-only mode (counts and
 * logs, never blocks) for safe threshold calibration before enabling enforcement.</p>
 */
@Service
@Slf4j
public class LoginAttemptService {

    private final boolean enabled;
    private final boolean enforce;
    private final int maxAttempts;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
            @Value("${vls.security.login.rate-limit.enabled:true}") boolean enabled,
            @Value("${vls.security.login.rate-limit.enforce:true}") boolean enforce,
            @Value("${vls.security.login.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${vls.security.login.rate-limit.window-seconds:300}") long windowSeconds,
            @Value("${vls.security.login.rate-limit.lockout-seconds:900}") long lockoutSeconds) {
        this(enabled, enforce, maxAttempts,
                Duration.ofSeconds(windowSeconds), Duration.ofSeconds(lockoutSeconds), Clock.systemUTC());
    }

    // Visible for testing (deterministic clock + explicit thresholds).
    public LoginAttemptService(boolean enabled, boolean enforce, int maxAttempts,
                               Duration window, Duration lockout, Clock clock) {
        this.enabled = enabled;
        this.enforce = enforce;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    /**
     * Throws {@code LOGIN_RATE_LIMITED} (HTTP 429) when the username or IP is currently locked and
     * enforcement is on. In log-only mode it only logs.
     */
    public void assertNotLocked(String username, String ip) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        boolean locked = isLocked(usernameKey(username), now) || isLocked(ipKey(ip), now);
        if (!locked) {
            return;
        }
        log.warn("Login rate limit triggered | user={} ip={} enforce={}", username, ip, enforce);
        if (enforce) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                    "Too many failed login attempts. Please try again later.");
        }
    }

    /** Records one failed attempt against both the username and the IP. */
    public void recordFailure(String username, String ip) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        registerFailure(usernameKey(username), now);
        registerFailure(ipKey(ip), now);
    }

    /** Clears counters for a successful authentication. */
    public void reset(String username, String ip) {
        counters.remove(usernameKey(username));
        counters.remove(ipKey(ip));
    }

    /** Test/ops helper: drop all tracked state. */
    public void clearAll() {
        counters.clear();
    }

    /** Whether the given username is currently locked (used by tests/diagnostics). */
    public boolean isUsernameLocked(String username) {
        return isLocked(usernameKey(username), clock.instant());
    }

    private boolean isLocked(String key, Instant now) {
        Counter counter = counters.get(key);
        return counter != null && counter.lockedUntil != null && now.isBefore(counter.lockedUntil);
    }

    private void registerFailure(String key, Instant now) {
        counters.compute(key, (ignored, existing) -> {
            Counter counter = (existing == null) ? new Counter() : existing;
            boolean currentlyLocked = counter.lockedUntil != null && now.isBefore(counter.lockedUntil);
            if (!currentlyLocked) {
                boolean windowExpired = counter.windowStart == null || now.isAfter(counter.windowStart.plus(window));
                if (windowExpired) {
                    counter.windowStart = now;
                    counter.failures = 0;
                    counter.lockedUntil = null;
                }
                counter.failures++;
                if (counter.failures >= maxAttempts) {
                    counter.lockedUntil = now.plus(lockout);
                }
            }
            return counter;
        });
    }

    private String usernameKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private String ipKey(String ip) {
        return "ip:" + (ip == null ? "" : ip.trim());
    }

    private static final class Counter {
        private int failures;
        private Instant windowStart;
        private Instant lockedUntil;
    }
}
