package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbNotificationRecipientEvent {
    private final JdbcTemplate jdbc;

    public DbNotificationRecipientEvent(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(long companyId, long eventId, int userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM %s WHERE event_id = ? AND user_id = ?
                """.formatted(TenantSqlIdentifiers.notificationRecipientEventTable(companyId)),
                Integer.class, eventId, userId);
        return count != null && count > 0;
    }

    public boolean insert(long companyId, long eventId, int userId,
                          long recipientId, int sequence) {
        return !jdbc.query("""
                        INSERT INTO %s (event_id, user_id, recipient_id, sequence_no)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (event_id, user_id) DO NOTHING
                        RETURNING recipient_id
                        """.formatted(TenantSqlIdentifiers.notificationRecipientEventTable(companyId)),
                        (rs, rowNum) -> rs.getLong(1), eventId, userId, recipientId, sequence)
                .isEmpty();
    }
}
