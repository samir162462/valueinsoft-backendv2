package com.example.valueinsoftbackend.fx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FxRateInitializationRunner {

    private final GlobalFxRateRefreshService refreshService;

    public FxRateInitializationRunner(GlobalFxRateRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMissingGlobalFxRate() {
        try {
            refreshService.initializeMissingRate();
        } catch (Exception exception) {
            log.warn("Initial global USD/EGP FX rate initialization failed; application will continue", exception);
        }
    }
}
