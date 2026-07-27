package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.repository.DbNotificationShutdownSchedule;
import com.example.valueinsoftbackend.notification.repository.DbNotificationShutdownSchedule.ShutdownSchedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    @Test
    void multipleSchedulesUseTheirUnionAndWeekdayBelongsToWindowStart() {
        NotificationResourceSaverProperties properties =
                new NotificationResourceSaverProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("rawtypes")
        HashOperations hash = mock(HashOperations.class);
        when(redis.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForHash()).thenReturn(hash);
        DbNotificationShutdownSchedule repository =
                mock(DbNotificationShutdownSchedule.class);
        NotificationOperatingWindowService service =
                new NotificationOperatingWindowService(
                        properties, redis, repository,
                        new ObjectMapper().findAndRegisterModules());

        UUID weekdayNight = UUID.randomUUID();
        UUID lunchBreak = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
                schedule(weekdayNight, "Weeknight", "22:00", "07:00", Set.of(1)),
                schedule(lunchBreak, "Lunch", "12:00", "13:00", Set.of(2))));

        service.resyncSchedulesFromDatabase();

        assertThat(service.isQuietAt(Instant.parse("2026-07-27T23:00:00Z"))).isTrue();
        assertThat(service.isQuietAt(Instant.parse("2026-07-28T06:59:00Z"))).isTrue();
        assertThat(service.isQuietAt(Instant.parse("2026-07-28T07:00:00Z"))).isFalse();
        assertThat(service.isQuietAt(Instant.parse("2026-07-28T12:30:00Z"))).isTrue();
        assertThat(service.nextTransitionAfter(Instant.parse("2026-07-28T12:30:00Z")))
                .isEqualTo(Instant.parse("2026-07-28T13:00:00Z"));
    }

    private static ShutdownSchedule schedule(
            UUID id, String name, String start, String end, Set<Integer> days) {
        return new ShutdownSchedule(
                id, name, true, LocalTime.parse(start), LocalTime.parse(end), "UTC",
                days, null, null, "test", 0, null, 0, null);
    }
}
