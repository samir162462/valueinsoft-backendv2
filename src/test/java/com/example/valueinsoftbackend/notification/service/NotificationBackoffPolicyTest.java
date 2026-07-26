package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationBackoffPolicyTest {
    @Test
    void followsScheduleJitterBoundsAndLetsRetryAfterWin() {
        NotificationProperties properties = new NotificationProperties();

        assertThat(new NotificationBackoffPolicy(properties, () -> 0.0)
                .delaySeconds(1, 0)).isEqualTo(48);
        assertThat(new NotificationBackoffPolicy(properties, () -> 0.5)
                .delaySeconds(2, 0)).isEqualTo(120);
        assertThat(new NotificationBackoffPolicy(properties, () -> 1.0)
                .delaySeconds(5, 0)).isEqualTo(25_920);
        assertThat(new NotificationBackoffPolicy(properties, () -> 0.0)
                .delaySeconds(3, 900)).isEqualTo(900);
    }
}
