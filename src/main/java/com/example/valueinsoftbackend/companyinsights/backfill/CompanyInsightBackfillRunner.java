package com.example.valueinsoftbackend.companyinsights.backfill;

import org.springframework.stereotype.Service;

/** Runs a requested backfill to completion without a timer or idle database polling. */
@Service
public class CompanyInsightBackfillRunner {

    private final CompanyInsightBackfillService backfillService;

    public CompanyInsightBackfillRunner(CompanyInsightBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    public void runToCompletion(long companyId, long backfillId) {
        while (backfillService.processNextChunk(companyId, backfillId)) {
            // Each iteration is one bounded, separately transactional date chunk.
        }
    }
}
