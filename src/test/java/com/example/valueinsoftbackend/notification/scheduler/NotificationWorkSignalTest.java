package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationResourceSaverProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationWorkSignalTest {

    @Test
    void unavailableRedisStillWakesTheLocalParkedWorker() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        NotificationWorkSignal signal =
                new NotificationWorkSignal(new NotificationResourceSaverProperties(), redis);
        AtomicReference<NotificationComponent> component = new AtomicReference<>();
        AtomicLong version = new AtomicLong();
        signal.addListener((nextComponent, nextVersion) -> {
            component.set(nextComponent);
            version.set(nextVersion);
        });

        signal.signal(NotificationComponent.FANOUT);

        assertThat(component).hasValue(NotificationComponent.FANOUT);
        assertThat(version).hasValue(1L);
        signal.shutdown();
    }
}
