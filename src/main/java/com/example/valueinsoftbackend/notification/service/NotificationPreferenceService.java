package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.Decision;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.EffectiveType;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.GlobalPreference;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.TypeOverride;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPreference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Preference reads, writes and evaluation (NC-5.2, NC-5.4).
 *
 * <p>The catalog is the source of defaults and preference rows are sparse overrides, so
 * every read merges the two. Writes go the other way: an override that matches the default
 * is deleted rather than stored, which keeps the table honest about what a user has
 * actually chosen.
 */
@Service
public class NotificationPreferenceService {

    private static final Set<String> PRIORITIES = Set.of("critical", "high", "normal", "low");
    private static final Set<String> DIGEST_MODES = Set.of("off", "hourly", "daily");

    private final DbNotificationCatalog catalog;
    private final DbNotificationPreference preferences;
    private final NotificationQuietHoursEvaluator evaluator;

    public NotificationPreferenceService(DbNotificationCatalog catalog,
                                         DbNotificationPreference preferences,
                                         NotificationQuietHoursEvaluator evaluator) {
        this.catalog = catalog;
        this.preferences = preferences;
        this.evaluator = evaluator;
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    public List<EffectiveType> effectiveTypes(long companyId, int userId) {
        Map<String, TypeOverride> overrides = preferences.overrideMap(companyId, userId);
        List<EffectiveType> result = new ArrayList<>();

        for (NotificationCatalogEntry entry : catalog.activeTypes()) {
            TypeOverride override = overrides.get(entry.typeKey());
            result.add(new EffectiveType(
                    entry.typeKey(),
                    entry.category(),
                    entry.defaultPriority(),
                    entry.userMutable(),
                    entry.bypassesQuietHours(),
                    override != null ? override.channelInApp() : entry.defaultChannelInApp(),
                    override != null ? override.channelPush() : entry.defaultChannelPush(),
                    override != null ? override.mutedUntil() : null,
                    override != null));
        }

        result.sort(Comparator.comparing(EffectiveType::category).thenComparing(EffectiveType::typeKey));
        return result;
    }

    public GlobalPreference globalPreference(long companyId, int userId) {
        return preferences.global(companyId, userId).orElseGet(GlobalPreference::defaults);
    }

    // ── Writes ─────────────────────────────────────────────────────────────

    public record TypeUpdate(String typeKey, Boolean channelInApp, Boolean channelPush, Instant mutedUntil) {
    }

    /**
     * Bulk upsert.
     *
     * <p>Immutable types are rejected with <strong>422 listing every offending key</strong>
     * rather than being silently dropped. A user who toggles a security notification off and
     * sees it snap back with no explanation will file a bug; being told which keys were
     * refused, and why, is the difference between a rule and a glitch.
     *
     * <p>The whole request is rejected if any key is invalid — a partial save would leave the
     * client's optimistic UI disagreeing with the server about the rest of the batch.
     */
    @Transactional
    public List<EffectiveType> updateTypes(long companyId, int userId, List<TypeUpdate> updates) {
        Map<String, NotificationCatalogEntry> byKey = new java.util.LinkedHashMap<>();
        for (NotificationCatalogEntry entry : catalog.activeTypes()) {
            byKey.put(entry.typeKey(), entry);
        }

        List<String> unknown = new ArrayList<>();
        List<String> immutable = new ArrayList<>();
        for (TypeUpdate update : updates) {
            NotificationCatalogEntry entry = byKey.get(update.typeKey());
            if (entry == null) {
                unknown.add(update.typeKey());
            } else if (!entry.userMutable()) {
                immutable.add(update.typeKey());
            }
        }

        if (!unknown.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_TYPE_UNKNOWN",
                    "Unknown notification types: " + String.join(", ", unknown));
        }
        if (!immutable.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_TYPE_IMMUTABLE",
                    "These notification types cannot be changed: " + String.join(", ", immutable));
        }

        for (TypeUpdate update : updates) {
            NotificationCatalogEntry entry = byKey.get(update.typeKey());
            boolean inApp = update.channelInApp() != null
                    ? update.channelInApp() : entry.defaultChannelInApp();
            boolean push = update.channelPush() != null
                    ? update.channelPush() : entry.defaultChannelPush();

            boolean matchesDefault = inApp == entry.defaultChannelInApp()
                    && push == entry.defaultChannelPush()
                    && update.mutedUntil() == null;

            if (matchesDefault) {
                // Storing a row identical to the default is noise. Deleting restores the
                // default and keeps the table sparse.
                preferences.deleteOverride(companyId, userId, update.typeKey());
            } else {
                preferences.upsertOverride(companyId, userId,
                        new TypeOverride(update.typeKey(), inApp, push, update.mutedUntil()));
            }
        }

        return effectiveTypes(companyId, userId);
    }

    @Transactional
    public GlobalPreference updateGlobal(long companyId, int userId, GlobalPreference requested) {
        validateGlobal(requested);
        preferences.upsertGlobal(companyId, userId, requested);
        return globalPreference(companyId, userId);
    }

    private void validateGlobal(GlobalPreference requested) {
        if (!PRIORITIES.contains(requested.minPriority())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_PRIORITY_INVALID",
                    "minPriority must be one of critical, high, normal, low");
        }
        if (!DIGEST_MODES.contains(requested.digestMode())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_DIGEST_INVALID",
                    "digestMode must be one of off, hourly, daily");
        }
        // Digest is Phase 7 (§6.8). Accepting the value now and silently not honouring it
        // would be worse than refusing it.
        if (!"off".equals(requested.digestMode())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_DIGEST_UNAVAILABLE",
                    "Digest mode is not available yet");
        }
        if ((requested.quietHoursStart() == null) != (requested.quietHoursEnd() == null)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_QUIET_HOURS_INCOMPLETE",
                    "quietHoursStart and quietHoursEnd must both be set or both be null");
        }
        try {
            ZoneId.of(requested.quietHoursTz());
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_TIMEZONE_INVALID",
                    "quietHoursTz must be an IANA zone name such as Africa/Cairo");
        }
    }

    // ── Evaluation, used by fan-out ────────────────────────────────────────

    /**
     * Called once per recipient during push materialisation. Two point lookups on indexed
     * primary keys; the fan-out batch already holds a transaction, so these join it.
     */
    public Decision decide(long companyId, int userId, NotificationCatalogEntry entry) {
        TypeOverride override = preferences.override(companyId, userId, entry.typeKey()).orElse(null);
        GlobalPreference global = preferences.global(companyId, userId).orElse(null);
        return evaluator.evaluate(entry, override, global);
    }

    /** Exposed for the preferences screen's "quiet hours are active right now" hint. */
    public boolean quietHoursActive(long companyId, int userId) {
        GlobalPreference global = globalPreference(companyId, userId);
        return evaluator.isWithinQuietHours(global, Instant.now());
    }

    public static LocalTime parseLocalTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }
}
