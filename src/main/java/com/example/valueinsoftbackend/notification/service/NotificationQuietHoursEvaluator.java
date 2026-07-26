package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.Decision;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.GlobalPreference;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.SuppressionReason;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.TypeOverride;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;

/**
 * Decides whether one notification may reach one user right now (NC-5.3, NC-5.4).
 *
 * <p><strong>v1 behaviour: suppress, do not defer.</strong> A non-critical push generated
 * during quiet hours is not sent, not queued, and not delivered later — the outbox row is
 * written {@code cancelled} so the suppression is auditable, and the in-app row is always
 * written. Waking to eleven queued pushes about last night is worse than one badge showing
 * eleven unread. Digest mode is the correct home for batched summaries and is Phase 7 work
 * (§6.8).
 *
 * <p>The evaluation order matters and is fixed: critical bypass, then in-app suppression,
 * then mute, then DND, then min-priority, then quiet hours. Mute is checked before DND so
 * the reason code reported to the operator is the specific one the user actually set.
 */
@Service
@Slf4j
public class NotificationQuietHoursEvaluator {

    private final Clock clock;

    public NotificationQuietHoursEvaluator() {
        this(Clock.systemUTC());
    }

    /**
     * Test seam. There is no {@code Clock} bean in this application — {@code LoginAttemptService}
     * and {@code ProviderCircuitBreaker} use the same pattern — and DST behaviour is untestable
     * without controlling time.
     */
    NotificationQuietHoursEvaluator(Clock clock) {
        this.clock = clock;
    }

    private static int rank(String priority) {
        return switch (priority) {
            case "critical" -> 0;
            case "high" -> 1;
            case "normal" -> 2;
            default -> 3;
        };
    }

    public Decision evaluate(NotificationCatalogEntry catalog,
                            TypeOverride override,
                            GlobalPreference global) {
        Instant now = clock.instant();
        GlobalPreference effective = global == null ? GlobalPreference.defaults() : global;

        // 1 · Critical types bypass ValueINSoft quiet hours and application DND. They do
        //     NOT bypass iOS Focus, silent mode, OS-level DND, or a muted Android channel —
        //     none of which the backend can see (§6.5).
        if (catalog.isCritical() || catalog.bypassesQuietHours()) {
            return Decision.allow();
        }

        // 2 · In-app suppression is the ONLY control that can remove the feed row.
        //     Everything below suppresses push only (invariant B-15).
        boolean inAppAllowed = override != null ? override.channelInApp() : catalog.defaultChannelInApp();

        // 3 · Type muted, either permanently (channelPush=false) or until a timestamp.
        if (override != null && (!override.channelPush() || override.isMutedAt(now))) {
            return new Decision(inAppAllowed, SuppressionReason.PREFERENCE_MUTED);
        }
        if (override == null && !catalog.defaultChannelPush()) {
            return new Decision(inAppAllowed, SuppressionReason.PREFERENCE_MUTED);
        }

        // 4 · Do not disturb — an absolute timestamp, "mute for two hours".
        if (effective.isDndActive(now)) {
            return new Decision(inAppAllowed, SuppressionReason.DND);
        }

        // 5 · Minimum priority floor across all types.
        if (rank(catalog.defaultPriority()) > rank(effective.minPriority())) {
            return new Decision(inAppAllowed, SuppressionReason.MIN_PRIORITY);
        }

        // 6 · Quiet hours.
        if (isWithinQuietHours(effective, now)) {
            return new Decision(inAppAllowed, SuppressionReason.QUIET_HOURS);
        }

        return new Decision(inAppAllowed, SuppressionReason.NONE);
    }

    /**
     * Quiet-hours window test, DST-correct.
     *
     * <p>The window is two local times in an IANA zone. Converting "now" into that zone and
     * comparing local times is what makes both DST transitions behave correctly, and it is
     * worth being explicit about why:
     *
     * <ul>
     *   <li><strong>Spring forward.</strong> On the day the clocks jump 02:00 → 03:00, no
     *       local time exists in that hour, so a 02:00–03:00 window simply never matches and
     *       nothing is suppressed. That is fail-open towards delivering the notification,
     *       which is the correct direction: suppression is the destructive choice.</li>
     *   <li><strong>Fall back.</strong> On the day 02:00–03:00 happens twice, the window is
     *       honoured twice. Expected, and harmless.</li>
     *   <li><strong>Spanning midnight.</strong> 22:00 → 07:00 is an OR, not an AND. Getting
     *       this backwards is the classic bug: the window would match for the 15 hours it is
     *       supposed to exclude.</li>
     * </ul>
     *
     * <p>An unknown or malformed zone falls back to UTC and logs once at WARN rather than
     * throwing — a bad timezone string in a preference row must not stop a notification.
     */
    boolean isWithinQuietHours(GlobalPreference global, Instant now) {
        if (!global.hasQuietHours()) {
            return false;
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(global.quietHoursTz());
        } catch ( java.time.DateTimeException ex) {
            log.warn("Unknown quiet-hours timezone '{}', falling back to UTC", global.quietHoursTz());
            zone = ZoneId.of("UTC");
        }

        LocalTime localNow = ZonedDateTime.ofInstant(now, zone).toLocalTime();
        LocalTime start = global.quietHoursStart();
        LocalTime end = global.quietHoursEnd();

        if (start.equals(end)) {
            // A zero-length window means "no quiet hours", not "always quiet". Treating it
            // as always-on would silence a user who set both fields to the same value by
            // accident, and they would have no way to tell why.
            return false;
        }

        if (start.isBefore(end)) {
            // Same-day window, e.g. 13:00 → 14:00.
            return !localNow.isBefore(start) && localNow.isBefore(end);
        }

        // Window spans midnight, e.g. 22:00 → 07:00.
        return !localNow.isBefore(start) || localNow.isBefore(end);
    }
}
