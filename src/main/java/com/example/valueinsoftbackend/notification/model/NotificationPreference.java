package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Preference records (NOTIFICATION_CENTER_PLAN.md §3.4, §6.8).
 *
 * <p>Preference rows are <strong>sparse</strong>: a missing row means "catalog defaults".
 * That is why there is no backfill in V170 — writing one row per user per type for a
 * 10k-user tenant would be several hundred thousand rows describing nothing.
 */
public final class NotificationPreference {

    private NotificationPreference() {
    }

    /** A per-type override. Absent means the catalog default applies. */
    public record TypeOverride(
            String typeKey,
            boolean channelInApp,
            boolean channelPush,
            Instant mutedUntil
    ) {
        public boolean isMutedAt(Instant now) {
            return mutedUntil != null && mutedUntil.isAfter(now);
        }
    }

    /**
     * Per-(user, company) global controls. Quiet hours are stored as an IANA zone name and
     * two local times, never as an offset — DST is then handled by {@code java.time} rather
     * than by arithmetic that is wrong twice a year (§11.7).
     */
    public record GlobalPreference(
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String quietHoursTz,
            Instant dndUntil,
            String minPriority,
            String digestMode
    ) {
        public static GlobalPreference defaults() {
            return new GlobalPreference(null, null, "UTC", null, "low", "off");
        }

        public boolean hasQuietHours() {
            return quietHoursStart != null && quietHoursEnd != null;
        }

        public boolean isDndActive(Instant now) {
            return dndUntil != null && dndUntil.isAfter(now);
        }
    }

    /**
     * The effective view of one type for one user — catalog defaults merged with any
     * override — as returned by {@code GET /preferences}.
     */
    public record EffectiveType(
            String typeKey,
            String category,
            String defaultPriority,
            boolean userMutable,
            boolean bypassesQuietHours,
            boolean channelInApp,
            boolean channelPush,
            Instant mutedUntil,
            /** True when the user has actually saved an override for this type. */
            boolean overridden
    ) {
    }

    /** Why a push was suppressed, mapped to the outbox {@code cancelled_reason} check. */
    public enum SuppressionReason {
        NONE(null),
        QUIET_HOURS("QUIET_HOURS"),
        DND("DND"),
        PREFERENCE_MUTED("PREFERENCE_MUTED"),
        MIN_PRIORITY("MIN_PRIORITY");

        private final String code;

        SuppressionReason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public boolean suppressed() {
            return this != NONE;
        }
    }

    /**
     * The outcome of evaluating one notification against one user's preferences.
     *
     * <p>{@code inAppAllowed} and {@code pushReason} are independent on purpose: turning
     * push off must never lose feed history (invariant B-15). The only control that
     * suppresses the in-app row is an explicit {@code channelInApp = false} override.
     */
    public record Decision(
            boolean inAppAllowed,
            SuppressionReason pushReason
    ) {
        public static Decision allow() {
            return new Decision(true, SuppressionReason.NONE);
        }

        public boolean pushAllowed() {
            return !pushReason.suppressed();
        }
    }
}
