package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationPreference.GlobalPreference;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.TypeOverride;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Preference storage (NC-5.1).
 *
 * <p>Rows are sparse. Every read here therefore returns "what the user actually saved",
 * and merging with catalog defaults is the service's job — keeping that split means the
 * repository never has to know what a default is.
 */
@Repository
public class DbNotificationPreference {

    private static final RowMapper<TypeOverride> TYPE_MAPPER = (rs, rowNum) -> new TypeOverride(
            rs.getString("type_key"),
            rs.getBoolean("channel_in_app"),
            rs.getBoolean("channel_push"),
            toInstant(rs.getTimestamp("muted_until")));

    private static final RowMapper<GlobalPreference> GLOBAL_MAPPER = (rs, rowNum) -> new GlobalPreference(
            toLocalTime(rs.getTime("quiet_hours_start")),
            toLocalTime(rs.getTime("quiet_hours_end")),
            rs.getString("quiet_hours_tz"),
            toInstant(rs.getTimestamp("dnd_until")),
            rs.getString("min_priority"),
            rs.getString("digest_mode"));

    private final JdbcTemplate jdbc;

    public DbNotificationPreference(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static LocalTime toLocalTime(java.sql.Time value) {
        return value == null ? null : value.toLocalTime();
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    public List<TypeOverride> overridesFor(long companyId, int userId) {
        return jdbc.query("""
                SELECT type_key, channel_in_app, channel_push, muted_until
                  FROM public.notification_preference
                 WHERE company_id = ? AND user_id = ?
                """, TYPE_MAPPER, companyId, userId);
    }

    public Map<String, TypeOverride> overrideMap(long companyId, int userId) {
        return overridesFor(companyId, userId).stream()
                .collect(Collectors.toMap(TypeOverride::typeKey, Function.identity()));
    }

    /** Single-type lookup on the fan-out hot path. */
    public Optional<TypeOverride> override(long companyId, int userId, String typeKey) {
        return jdbc.query("""
                SELECT type_key, channel_in_app, channel_push, muted_until
                  FROM public.notification_preference
                 WHERE company_id = ? AND user_id = ? AND type_key = ?
                """, TYPE_MAPPER, companyId, userId, typeKey).stream().findFirst();
    }

    public Optional<GlobalPreference> global(long companyId, int userId) {
        return jdbc.query("""
                SELECT quiet_hours_start, quiet_hours_end, quiet_hours_tz,
                       dnd_until, min_priority, digest_mode
                  FROM public.notification_preference_global
                 WHERE company_id = ? AND user_id = ?
                """, GLOBAL_MAPPER, companyId, userId).stream().findFirst();
    }

    // ── Writes ─────────────────────────────────────────────────────────────

    /**
     * Upsert one type override. Uses the natural primary key for conflict inference —
     * a plain column list, not an index name, which PostgreSQL would reject.
     */
    public void upsertOverride(long companyId, int userId, TypeOverride override) {
        jdbc.update("""
                INSERT INTO public.notification_preference
                       (user_id, company_id, type_key, channel_in_app, channel_push,
                        muted_until, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (user_id, company_id, type_key) DO UPDATE SET
                    channel_in_app = EXCLUDED.channel_in_app,
                    channel_push   = EXCLUDED.channel_push,
                    muted_until    = EXCLUDED.muted_until,
                    updated_at     = NOW()
                """,
                userId, companyId, override.typeKey(),
                override.channelInApp(), override.channelPush(),
                override.mutedUntil() == null ? null : Timestamp.from(override.mutedUntil()));
    }

    /**
     * Removing an override restores the catalog default. This is what "reset" means, and
     * it keeps the table sparse instead of accumulating rows that say nothing.
     */
    public void deleteOverride(long companyId, int userId, String typeKey) {
        jdbc.update("""
                DELETE FROM public.notification_preference
                 WHERE company_id = ? AND user_id = ? AND type_key = ?
                """, companyId, userId, typeKey);
    }

    public void upsertGlobal(long companyId, int userId, GlobalPreference global) {
        jdbc.update("""
                INSERT INTO public.notification_preference_global
                       (user_id, company_id, quiet_hours_start, quiet_hours_end,
                        quiet_hours_tz, dnd_until, min_priority, digest_mode, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (user_id, company_id) DO UPDATE SET
                    quiet_hours_start = EXCLUDED.quiet_hours_start,
                    quiet_hours_end   = EXCLUDED.quiet_hours_end,
                    quiet_hours_tz    = EXCLUDED.quiet_hours_tz,
                    dnd_until         = EXCLUDED.dnd_until,
                    min_priority      = EXCLUDED.min_priority,
                    digest_mode       = EXCLUDED.digest_mode,
                    updated_at        = NOW()
                """,
                userId, companyId,
                global.quietHoursStart() == null ? null : java.sql.Time.valueOf(global.quietHoursStart()),
                global.quietHoursEnd() == null ? null : java.sql.Time.valueOf(global.quietHoursEnd()),
                global.quietHoursTz(),
                global.dndUntil() == null ? null : Timestamp.from(global.dndUntil()),
                global.minPriority(),
                global.digestMode());
    }
}
