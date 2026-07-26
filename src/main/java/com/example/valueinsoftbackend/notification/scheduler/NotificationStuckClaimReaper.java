package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.service.NotificationFanOutService;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPushOutbox;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
public class NotificationStuckClaimReaper implements NotificationWorkerTask {
    private final JdbcTemplate jdbc;
    private final NotificationFanOutService fanOut;
    private final NotificationProperties properties;
    private final DbNotificationPushOutbox pushOutbox;
    private final String reaperId = "notification-reaper-"
            + java.util.UUID.randomUUID();

    public NotificationStuckClaimReaper(JdbcTemplate jdbc,
                                        NotificationFanOutService fanOut,
                                        NotificationProperties properties,
                                        DbNotificationPushOutbox pushOutbox) {
        this.jdbc = jdbc;
        this.fanOut = fanOut;
        this.properties = properties;
        this.pushOutbox = pushOutbox;
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.STUCK_CLAIM_REAPER;
    }

    @Override
    public Duration delay() {
        return Duration.ofMillis(properties.getReaper().getPollDelayMs());
    }

    @Override
    @Transactional
    public void runCycle() {
        for (long companyId : fanOut.tenantIds()) {
            String table = TenantSqlIdentifiers.notificationFanOutJobTable(companyId);
            jdbc.update("""
                    WITH expired AS (
                      SELECT job_id FROM %s
                      WHERE status = 'claimed' AND claim_expires_at < NOW()
                      ORDER BY claim_expires_at
                      FOR UPDATE SKIP LOCKED LIMIT ?
                    )
                    UPDATE %s j
                    SET status = CASE WHEN attempt_count >= max_attempts THEN 'dead' ELSE 'failed' END,
                        claimed_by = NULL, claimed_at = NULL, claim_expires_at = NULL,
                        next_attempt_at = NOW(), last_error = 'claim lease expired'
                    FROM expired e WHERE j.job_id = e.job_id
                    """.formatted(table, table), properties.getReaper().getBatchSize());
        }
        pushOutbox.releaseExpiredClaims(
                properties.getReaper().getBatchSize(),
                (int) properties.getDispatch().getBackoffSeconds()[0],
                reaperId);
    }
}
