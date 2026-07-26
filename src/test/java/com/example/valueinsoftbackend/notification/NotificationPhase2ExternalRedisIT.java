package com.example.valueinsoftbackend.notification;

import com.example.valueinsoftbackend.notification.config.NotificationControlProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlService;
import com.example.valueinsoftbackend.notification.repository.DbNotificationControl;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeed;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import com.example.valueinsoftbackend.notification.service.NotificationSummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real Redis-compatible-server verification for the Phase 2 control plane and summary cache.
 *
 * <p>Run with:
 * {@code VLS_NOTIFICATION_REDIS_HOST=127.0.0.1 VLS_NOTIFICATION_REDIS_PORT=6387
 * mvn -Dtest=NotificationPhase2ExternalRedisIT test}.
 */
@EnabledIfEnvironmentVariable(named = "VLS_NOTIFICATION_REDIS_HOST", matches = ".+")
class NotificationPhase2ExternalRedisIT {
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void connect() {
        String host = System.getenv("VLS_NOTIFICATION_REDIS_HOST");
        int port = Integer.parseInt(
                System.getenv().getOrDefault("VLS_NOTIFICATION_REDIS_PORT", "6379"));
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        flushDatabase();
    }

    @AfterEach
    void disconnect() {
        if (redis != null) {
            flushDatabase();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void controlChangePropagatesThroughRealRedisPubSub() throws Exception {
        NotificationControlProperties properties = new NotificationControlProperties();
        properties.setKeyPrefix("phase2:test:control");
        properties.setChangeChannel("phase2:test:control:changed");

        DbNotificationControl publisherRepository = mock(DbNotificationControl.class);
        DbNotificationControl.ControlState disabled = new DbNotificationControl.ControlState(
                "worker", "FANOUT", false, "QUEUE", "phase 2 integration",
                null, 42, 101);
        when(publisherRepository.change(
                NotificationComponent.FANOUT,
                false,
                "QUEUE",
                "phase 2 integration",
                null,
                42,
                "127.0.0.1",
                7)).thenReturn(disabled);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        NotificationControlService publisher = new NotificationControlService(
                properties, redisProvider, publisherRepository);
        NotificationControlService subscriber = new NotificationControlService(
                properties, redisProvider, mock(DbNotificationControl.class));

        RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(connectionFactory);
        listener.addMessageListener(
                (message, pattern) -> subscriber.refresh(),
                new ChannelTopic(properties.getChangeChannel()));
        listener.afterPropertiesSet();
        listener.start();
        try {
            publisher.change(
                    NotificationComponent.FANOUT,
                    false,
                    "queue",
                    " phase 2 integration ",
                    null,
                    42,
                    "127.0.0.1",
                    7);

            long deadline = System.nanoTime() + 5_000_000_000L;
            while (subscriber.isEnabled(NotificationComponent.FANOUT)
                    && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }

            assertThat(redis.<String, String>opsForHash().get(
                    properties.getKeyPrefix(), "worker:FANOUT")).isEqualTo("false");
            assertThat(redis.opsForValue().get(properties.getKeyPrefix() + ":version"))
                    .isEqualTo("101");
            assertThat(publisher.source()).isEqualTo("redis");
            assertThat(subscriber.source()).isEqualTo("redis");
            assertThat(subscriber.isEnabled(NotificationComponent.FANOUT)).isFalse();
        } finally {
            listener.stop();
            listener.destroy();
        }
    }

    @Test
    void summaryCacheHitsAndInvalidatesAgainstRealRedis() {
        DbNotificationFeed feed = mock(DbNotificationFeed.class);
        NotificationAudienceResolver audience = mock(NotificationAudienceResolver.class);
        when(feed.summaryRows(81, 23)).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        NotificationSummaryService service = new NotificationSummaryService(
                feed, audience, redisProvider, new ObjectMapper());

        assertThat(service.summary(81, 23).unreadCount()).isZero();
        assertThat(service.summary(81, 23).unreadCount()).isZero();
        verify(feed, times(1)).summaryRows(81, 23);
        assertThat(redis.hasKey("notif:81:summary:23")).isTrue();

        service.invalidateAfterCommit(81, 23);
        assertThat(redis.hasKey("notif:81:summary:23")).isFalse();

        assertThat(service.summary(81, 23).unreadCount()).isZero();
        verify(feed, times(2)).summaryRows(81, 23);
    }

    private void flushDatabase() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
