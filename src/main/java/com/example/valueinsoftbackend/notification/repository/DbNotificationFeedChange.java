package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbNotificationFeedChange {
    private final JdbcTemplate jdbc;

    public DbNotificationFeedChange(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long nextSequence(long companyId) {
        return jdbc.queryForObject("SELECT nextval('" +
                TenantSqlIdentifiers.notificationFeedChangeSequence(companyId) + "')",
                Long.class);
    }

    public void insert(long companyId, long sequence, int userId, long recipientId,
                       String type, Long eventId) {
        jdbc.update("""
                INSERT INTO %s
                    (change_sequence, user_id, recipient_id, change_type, event_id)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(TenantSqlIdentifiers.notificationFeedChangeTable(companyId)),
                sequence, userId, recipientId, type, eventId);
    }
}
