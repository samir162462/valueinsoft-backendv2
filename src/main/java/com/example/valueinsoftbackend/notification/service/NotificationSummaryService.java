package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.model.NotificationSummary;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeed;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;

@Service
public class NotificationSummaryService {
    private final DbNotificationFeed feed;
    private final NotificationAudienceResolver audience;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;

    public NotificationSummaryService(DbNotificationFeed feed,
                                      NotificationAudienceResolver audience,
                                      ObjectProvider<StringRedisTemplate> redisProvider,
                                      ObjectMapper objectMapper) {
        this.feed = feed;
        this.audience = audience;
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
    }

    public NotificationSummary summary(long companyId, int userId) {
        String key = key(companyId, userId);
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, NotificationSummary.class);
                }
            } catch (Exception ignored) {
                // Redis is an optimization; PostgreSQL is authoritative.
            }
        }

        long unseen = 0;
        long unread = 0;
        long sequence = 0;
        Instant lastEvent = null;
        for (DbNotificationFeed.SummaryRow row : feed.summaryRows(companyId, userId)) {
            if (!audience.userHasCapability(companyId, userId, row.branchId(),
                    row.requiredCapability())) {
                continue;
            }
            if ("unseen".equals(row.state())) {
                unseen++;
            }
            if ("unseen".equals(row.state()) || "seen".equals(row.state())) {
                unread++;
            }
            if (lastEvent == null || row.lastEventAt().isAfter(lastEvent)) {
                lastEvent = row.lastEventAt();
            }
            sequence = Math.max(sequence, row.changeSequence());
        }
        NotificationSummary result =
                new NotificationSummary(unseen, unread, lastEvent, sequence, companyId);
        if (redis != null) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(result),
                        Duration.ofSeconds(30));
            } catch (Exception ignored) {
                // A cache outage must not fail the feed.
            }
        }
        return result;
    }

    public void invalidateAfterCommit(long companyId, int userId) {
        Runnable invalidation = () -> {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                try {
                    redis.delete(key(companyId, userId));
                } catch (RuntimeException ignored) {
                    // Cache expiry is the fallback.
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            invalidation.run();
                        }
                    });
        } else {
            invalidation.run();
        }
    }

    private static String key(long companyId, int userId) {
        return "notif:" + companyId + ":summary:" + userId;
    }
}
