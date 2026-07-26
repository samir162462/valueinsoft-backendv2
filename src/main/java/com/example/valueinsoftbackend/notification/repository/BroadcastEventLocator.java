package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Finds the tenant {@code notification_event} a broadcast produced.
 *
 * <p>The link is {@code notification_event.broadcast_id}, which is why that column exists and
 * why {@code chk_ne_broadcast_source} requires it whenever {@code source = 'broadcast'}: the
 * materialisation worker holds a batch row in {@code public} and has to reach the event in the
 * tenant schema without carrying the id through three tables.
 */
@Repository
public class BroadcastEventLocator {

    private final JdbcTemplate jdbc;
    private final DbNotificationEvent events;

    public BroadcastEventLocator(JdbcTemplate jdbc, DbNotificationEvent events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public Optional<Long> findEventId(long companyId, long broadcastId) {
        return jdbc.query("""
                        SELECT event_id FROM %s
                         WHERE broadcast_id = ? AND source = 'broadcast'
                         ORDER BY event_id
                         LIMIT 1
                        """.formatted(TenantSqlIdentifiers.notificationEventTable(companyId)),
                        (rs, rowNum) -> rs.getLong("event_id"), broadcastId)
                .stream().findFirst();
    }

    public NotificationEvent requireForBroadcast(long companyId, long broadcastId) {
        long eventId = findEventId(companyId, broadcastId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "NOTIFICATION_BROADCAST_EVENT_MISSING",
                        "Broadcast " + broadcastId + " has no tenant event; planning did not complete"));
        return events.require(companyId, eventId);
    }
}
