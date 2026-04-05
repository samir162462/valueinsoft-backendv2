package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
@ConditionalOnProperty(
        name = "platform.admin.metrics.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PlatformAdminMetricsScheduler {

    private final PlatformAdminMetricsService platformAdminMetricsService;

    public PlatformAdminMetricsScheduler(PlatformAdminMetricsService platformAdminMetricsService) {
        this.platformAdminMetricsService = platformAdminMetricsService;
    }

    @Scheduled(
            cron = "${platform.admin.metrics.scheduler.cron:0 30 2 * * *}",
            zone = "${platform.admin.metrics.scheduler.zone:Africa/Cairo}"
    )
    public void refreshDailyMetricsSnapshot() {
        try {
            platformAdminMetricsService.refreshDailyMetricsForSystemSchedule(LocalDate.now());
        } catch (Exception exception) {
            log.warn("Scheduled platform daily metrics refresh failed", exception);
        }
    }
}
