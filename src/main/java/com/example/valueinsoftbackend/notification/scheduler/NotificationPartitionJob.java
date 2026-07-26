package com.example.valueinsoftbackend.notification.scheduler;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.ControlComponent;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Pre-creates notification queue partitions through the Flyway-owned database function.
 *
 * <p>The function takes the transaction-scoped advisory lock, so this task is safe when every
 * application instance runs it. The default partitions remain the lossless safety net.
 */
@Component
@ConditionalOnProperty(name = "valueinsoft.notification.enabled", havingValue = "true")
@Slf4j
public class NotificationPartitionJob implements NotificationWorkerTask {

    private static final Duration DAILY = Duration.ofDays(1);
    private static final Duration FIRST_RUN_DELAY = Duration.ofMinutes(1);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationProperties properties;

    public NotificationPartitionJob(JdbcTemplate jdbcTemplate, NotificationProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public ControlComponent component() {
        return NotificationComponent.PARTITION_MAINTENANCE;
    }

    @Override
    public Duration delay() {
        return DAILY;
    }

    @Override
    public Duration initialDelay() {
        return FIRST_RUN_DELAY;
    }

    @Override
    public void runCycle() {
        int monthsAhead = properties.getRetention().getPartitionsAheadMonths();
        Integer created = jdbcTemplate.queryForObject(
                "SELECT public.notification_partition_maintenance(?)",
                Integer.class,
                monthsAhead);
        if (created != null && created > 0) {
            log.info("Created {} notification queue partition(s), horizon={} month(s)",
                    created, monthsAhead);
        }
    }
}
