package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationOperatingWindowServiceTest {

    @Test
    void overnightFallbackWindowUsesNoDatabaseAndCalculatesBothSidesOfMidnight() {
        NotificationResourceSaverProperties properties =
                new NotificationResourceSaverProperties();
        properties.setQuietWindowEnabled(true);
        properties.setQuietStart("22:00");
        properties.setQuietEnd("07:00");
        properties.setTimezone("UTC");
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);

        NotificationOperatingWindowService service =
                new NotificationOperatingWindowService(properties, redis);
        service.initialize();

        assertThat(service.isQuietAt(Instant.parse("2026-07-26T23:00:00Z"))).isTrue();
        assertThat(service.isQuietAt(Instant.parse("2026-07-27T06:59:00Z"))).isTrue();
        assertThat(service.isQuietAt(Instant.parse("2026-07-27T07:00:00Z"))).isFalse();
        assertThat(service.nextTransitionAfter(Instant.parse("2026-07-26T23:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-27T07:00:00Z"));
    }

    @Test
    void rejectsZeroLengthAndInvalidTimezoneWindows() {
        NotificationResourceSaverProperties properties =
                new NotificationResourceSaverProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        NotificationOperatingWindowService service =
                new NotificationOperatingWindowService(properties, redis);

        assertThatThrownBy(() -> service.update(true, "22:00", "22:00", "UTC"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update(true, "22:00", "07:00", "Cairo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
