package com.example.valueinsoftbackend.notification.stream;

import com.example.valueinsoftbackend.notification.model.NotificationFeedItem;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeed;
import com.example.valueinsoftbackend.notification.service.NotificationSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Wires the Redis pattern subscriptions that drive live SSE delivery (NC-6.5, NC-6.7).
 *
 * <p>Pattern subscriptions rather than one channel per user: a per-user channel would mean
 * subscribing and unsubscribing on every connection, and Redis pattern matching is cheaper
 * than that churn at POS scale.
 *
 * <p>Every instance receives every hint and drops the ones for users it does not hold. That
 * is a deliberate trade: the alternative — a directory of which instance holds which user —
 * needs its own consistency story and goes stale exactly when an instance dies.
 */
@Configuration
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class NotificationStreamRedisConfig {

    @Bean
    RedisMessageListenerContainer notificationStreamListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisNotificationRelay relay,
            SseConnectionRegistry registry,
            DbNotificationFeed feed,
            NotificationSummaryService summaries) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        container.addMessageListener((message, pattern) -> {
            RedisNotificationRelay.FeedChangeHint hint = relay.parseHint(
                    new String(message.getBody(), StandardCharsets.UTF_8));
            if (hint == null) return;

            List<SseConnectionRegistry.Connection> connections =
                    registry.connectionsFor(hint.companyId(), hint.userId());
            if (connections.isEmpty()) {
                // Normal: this instance does not hold that user's stream.
                return;
            }

            try {
                // The hint carries no content, so read the current item here. This is one
                // indexed query per instance that actually holds a connection for the user,
                // and it guarantees the client sees committed state rather than whatever the
                // publisher had in memory.
                List<NotificationFeedItem> items = feed.replaySince(
                        hint.companyId(), hint.userId(), hint.changeSequence() - 1, 1);
                var summary = summaries.summary(hint.companyId(), hint.userId());

                for (SseConnectionRegistry.Connection connection : connections) {
                    for (NotificationFeedItem item : items) {
                        registry.send(connection, NotificationStreamEvent.NOTIFICATION,
                                item.changeSequence(), item);
                    }
                    registry.send(connection, NotificationStreamEvent.SUMMARY, null, summary);
                }
            } catch (RuntimeException ex) {
                // A failed live push is recoverable by reconnect; never let it kill the
                // listener container.
                log.warn("SSE live delivery failed for company {} user {}: {}",
                        hint.companyId(), hint.userId(), ex.toString());
            }
        }, new PatternTopic(RedisNotificationRelay.USER_CHANNEL_PATTERN));

        container.addMessageListener((message, pattern) -> {
            RedisNotificationRelay.SessionKill kill = relay.parseSessionKill(
                    new String(message.getBody(), StandardCharsets.UTF_8));
            if (kill == null) return;
            registry.closeSession(kill.companyId(), kill.sessionId(), kill.reason());
        }, new PatternTopic(RedisNotificationRelay.SESSION_KILL_PATTERN));

        return container;
    }
}
