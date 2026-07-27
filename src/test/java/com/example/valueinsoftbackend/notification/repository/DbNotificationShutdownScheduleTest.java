package com.example.valueinsoftbackend.notification.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbNotificationShutdownScheduleTest {

    @Test
    void returningClauseIsSeparatedFromFirstReturnedColumn() {
        assertThat(DbNotificationShutdownSchedule.INSERT_SQL)
                .containsPattern("RETURNING\\s+schedule_uuid");
        assertThat(DbNotificationShutdownSchedule.UPDATE_SQL)
                .containsPattern("RETURNING\\s+schedule_uuid");
        assertThat(DbNotificationShutdownSchedule.INSERT_SQL)
                .doesNotContain("RETURNINGschedule_uuid");
        assertThat(DbNotificationShutdownSchedule.UPDATE_SQL)
                .doesNotContain("RETURNINGschedule_uuid");
    }
}
