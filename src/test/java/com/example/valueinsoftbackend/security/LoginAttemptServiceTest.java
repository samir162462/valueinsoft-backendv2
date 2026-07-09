package com.example.valueinsoftbackend.security;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.SecurityPack.LoginAttemptService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1-5 unit tests for {@link LoginAttemptService}: lockout after threshold, reset on success,
 * rolling-window expiry, per-IP lockout, and log-only mode.
 */
class LoginAttemptServiceTest {

    private static final String USER = "alice";
    private static final String OTHER_USER = "bob";
    private static final String IP = "203.0.113.7";
    private static final Duration WINDOW = Duration.ofSeconds(300);
    private static final Duration LOCKOUT = Duration.ofSeconds(900);

    private TestClock clock;

    private LoginAttemptService newService(boolean enforce) {
        clock = new TestClock(Instant.parse("2026-07-09T00:00:00Z"));
        return new LoginAttemptService(true, enforce, 5, WINDOW, LOCKOUT, clock);
    }

    @Test
    void locksAfterMaxAttempts_andThrows429() {
        LoginAttemptService service = newService(true);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> service.assertNotLocked(USER, IP));
            service.recordFailure(USER, IP);
        }

        ApiException ex = assertThrows(ApiException.class, () -> service.assertNotLocked(USER, IP));
        assertEquals("LOGIN_RATE_LIMITED", ex.getCode());
    }

    @Test
    void successfulReset_clearsLockCountersBeforeThreshold() {
        LoginAttemptService service = newService(true);

        for (int i = 0; i < 4; i++) {
            service.recordFailure(USER, IP);
        }
        service.reset(USER, IP);

        // A fresh failure after reset does not immediately lock.
        service.recordFailure(USER, IP);
        assertDoesNotThrow(() -> service.assertNotLocked(USER, IP));
    }

    @Test
    void windowExpiry_resetsFailureCount() {
        LoginAttemptService service = newService(true);

        for (int i = 0; i < 4; i++) {
            service.recordFailure(USER, IP);
        }
        // Advance beyond the rolling window; prior failures should no longer count.
        clock.advance(WINDOW.plusSeconds(1));

        service.recordFailure(USER, IP); // counts as 1 in a fresh window
        assertDoesNotThrow(() -> service.assertNotLocked(USER, IP));
    }

    @Test
    void lockExpiresAfterLockoutDuration() {
        LoginAttemptService service = newService(true);

        for (int i = 0; i < 5; i++) {
            service.recordFailure(USER, IP);
        }
        assertThrows(ApiException.class, () -> service.assertNotLocked(USER, IP));

        clock.advance(LOCKOUT.plusSeconds(1));
        assertDoesNotThrow(() -> service.assertNotLocked(USER, IP));
    }

    @Test
    void ipLock_blocksDifferentUsernameFromSameIp() {
        LoginAttemptService service = newService(true);

        // Five failures for USER also lock the shared IP.
        for (int i = 0; i < 5; i++) {
            service.recordFailure(USER, IP);
        }

        ApiException ex = assertThrows(ApiException.class, () -> service.assertNotLocked(OTHER_USER, IP));
        assertEquals("LOGIN_RATE_LIMITED", ex.getCode());
    }

    @Test
    void logOnlyMode_neverThrows() {
        LoginAttemptService service = newService(false);

        for (int i = 0; i < 10; i++) {
            service.recordFailure(USER, IP);
        }
        assertDoesNotThrow(() -> service.assertNotLocked(USER, IP));
    }

    private static final class TestClock extends Clock {
        private Instant now;

        private TestClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
