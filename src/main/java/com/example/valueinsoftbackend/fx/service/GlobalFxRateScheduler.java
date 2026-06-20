package com.example.valueinsoftbackend.fx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "fx.deepseek.schedule.enabled", havingValue = "true")
public class GlobalFxRateScheduler {

    private final GlobalFxRateRefreshService refreshService;

    public GlobalFxRateScheduler(GlobalFxRateRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Scheduled(
            cron = "${fx.deepseek.schedule.cron:0 0 2 * * *}",
            zone = "${fx.deepseek.schedule.time-zone:Africa/Cairo}"
    )
    public void refreshDailyUsdEgpRate() {
        try {
            refreshService.runDailySchedule();
        } catch (Exception exception) {
            log.warn("Scheduled global USD/EGP FX rate refresh failed", exception);
        }
    }
}
