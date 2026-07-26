package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbNotificationRetention {
    private static final int MAX_BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbc;

    public DbNotificationRetention(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Deletes only expired events that are no longer referenced by lineage, recipient
     * snapshots, or fan-out work. The future retention worker can call this bounded step
     * without relying on a foreign-key failure as its normal control flow.
     */
    public int purgeUnreferencedEvents(long companyId, int batchSize) {
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Notification retention batch size must be between 1 and "
                            + MAX_BATCH_SIZE);
        }
        String eventTable = TenantSqlIdentifiers.notificationEventTable(companyId);
        String lineageTable = TenantSqlIdentifiers.notificationRecipientEventTable(companyId);
        String recipientTable = TenantSqlIdentifiers.notificationRecipientTable(companyId);
        String jobTable = TenantSqlIdentifiers.notificationFanOutJobTable(companyId);
        return jdbc.query("""
                        WITH candidates AS (
                            SELECT event.event_id
                            FROM %s event
                            WHERE event.created_at
                                      < NOW() - make_interval(days => event.retention_days)
                              AND NOT EXISTS (
                                  SELECT 1 FROM %s lineage
                                  WHERE lineage.event_id = event.event_id)
                              AND NOT EXISTS (
                                  SELECT 1 FROM %s recipient
                                  WHERE recipient.first_event_id = event.event_id
                                     OR recipient.latest_event_id = event.event_id)
                              AND NOT EXISTS (
                                  SELECT 1 FROM %s job
                                  WHERE job.event_id = event.event_id)
                            ORDER BY event.created_at, event.event_id
                            FOR UPDATE SKIP LOCKED
                            LIMIT ?
                        )
                        DELETE FROM %s event
                        USING candidates
                        WHERE event.event_id = candidates.event_id
                        RETURNING event.event_id
                        """.formatted(eventTable, lineageTable, recipientTable, jobTable,
                                eventTable),
                        (rs, rowNum) -> rs.getLong(1), batchSize)
                .size();
    }
}
