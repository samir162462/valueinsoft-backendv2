package com.example.valueinsoftbackend.notification.control;

import com.example.valueinsoftbackend.notification.config.NotificationControlProperties;
import com.example.valueinsoftbackend.notification.repository.DbNotificationControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControlServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void persistsAuditCopyBeforePublishingRedisChangeAfterCommit() {
        NotificationControlProperties properties = new NotificationControlProperties();
        DbNotificationControl repository = mock(DbNotificationControl.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForHash()).thenReturn(hash);
        when(redis.opsForValue()).thenReturn(values);
        when(hash.entries(properties.getKeyPrefix()))
                .thenReturn(Map.of("worker:FANOUT", "false"));

        DbNotificationControl.ControlState persisted =
                new DbNotificationControl.ControlState(
                        "worker", "FANOUT", false, "QUEUE", "maintenance",
                        null, 42, 91);
        when(repository.change(
                NotificationComponent.FANOUT, false, "QUEUE", "maintenance",
                null, 42, "127.0.0.1", 8)).thenReturn(persisted);
        NotificationControlService service =
                new NotificationControlService(properties, provider, repository);

        TransactionSynchronizationManager.initSynchronization();
        service.change(NotificationComponent.FANOUT, false, "queue", " maintenance ",
                null, 42, "127.0.0.1", 8);

        verify(repository).change(
                NotificationComponent.FANOUT, false, "QUEUE", "maintenance",
                null, 42, "127.0.0.1", 8);
        verify(redis, never()).convertAndSend(
                properties.getChangeChannel(), "91");

        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(hash).put(properties.getKeyPrefix(), "worker:FANOUT", "false");
        verify(values).set(properties.getKeyPrefix() + ":version", "91");
        verify(redis).convertAndSend(properties.getChangeChannel(), "91");
    }

    @Test
    void disableRequiresReasonAndExpiryJobCannotBeChanged() {
        NotificationControlProperties properties = new NotificationControlProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        NotificationControlService service = new NotificationControlService(
                properties, provider, mock(DbNotificationControl.class));

        assertThatThrownBy(() -> service.change(
                NotificationComponent.FANOUT, false, "SUPPRESS", " ",
                null, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> service.change(
                NotificationComponent.CONTROL_EXPIRY, true, "SUPPRESS", null,
                null, 1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be disabled");
    }
}
