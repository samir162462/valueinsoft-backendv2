package com.example.valueinsoftbackend.companyinsights.backfill;

import com.example.valueinsoftbackend.companyinsights.config.CompanyInsightProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains backfill checkpoints one chunk at a time on a fixed cadence, so historical
 * backfills progress in the background without ever blocking an API request.
 */
@Component
@Slf4j
public class CompanyInsightBackfillWorker {

    private static final int MAX_CHUNKS_PER_TICK = 3;

    private final CompanyInsightBackfillService backfillService;
    private final CompanyInsightProperties properties;

    public CompanyInsightBackfillWorker(CompanyInsightBackfillService backfillService,
                                        CompanyInsightProperties properties) {
        this.backfillService = backfillService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vls.company-insights.backfill.fixed-delay-ms:60000}")
    public void drain() {
        if (!properties.isEnabled()) {
            return;
        }
        for (int i = 0; i < MAX_CHUNKS_PER_TICK; i++) {
            boolean worked;
            try {
                worked = backfillService.processNextChunk();
            } catch (RuntimeException exception) {
                log.warn("Backfill worker tick failed reason={}", exception.getMessage());
                return;
            }
            if (!worked) {
                return; // nothing pending
            }
        }
    }
}
