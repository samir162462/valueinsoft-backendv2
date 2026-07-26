package com.example.valueinsoftbackend.notification.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DbNotificationRetentionTest {
    @Test
    void rejectsUnboundedBatchSizesBeforeIssuingSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DbNotificationRetention retention = new DbNotificationRetention(jdbc);

        assertThatThrownBy(() -> retention.purgeUnreferencedEvents(1095, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retention.purgeUnreferencedEvents(1095, 5_001))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
}
