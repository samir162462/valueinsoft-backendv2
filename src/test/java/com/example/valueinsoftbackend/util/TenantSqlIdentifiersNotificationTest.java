package com.example.valueinsoftbackend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantSqlIdentifiersNotificationTest {

    @Test
    void buildsAllNotificationTenantIdentifiers() {
        assertEquals("c_42.notification_event",
                TenantSqlIdentifiers.notificationEventTable(42));
        assertEquals("c_42.notification_fanout_job",
                TenantSqlIdentifiers.notificationFanOutJobTable(42));
        assertEquals("c_42.notification_recipient",
                TenantSqlIdentifiers.notificationRecipientTable(42));
        assertEquals("c_42.notification_recipient_event",
                TenantSqlIdentifiers.notificationRecipientEventTable(42));
        assertEquals("c_42.notification_feed_change",
                TenantSqlIdentifiers.notificationFeedChangeTable(42));
        assertEquals("c_42.notification_recipient_audit",
                TenantSqlIdentifiers.notificationRecipientAuditTable(42));
        assertEquals("c_42.notification_feed_change_seq",
                TenantSqlIdentifiers.notificationFeedChangeSequence(42));
    }

    @Test
    void rejectsNonPositiveCompanyIds() {
        assertThrows(IllegalArgumentException.class,
                () -> TenantSqlIdentifiers.notificationEventTable(0));
    }
}

