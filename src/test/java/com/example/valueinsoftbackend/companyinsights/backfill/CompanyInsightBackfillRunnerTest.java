package com.example.valueinsoftbackend.companyinsights.backfill;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyInsightBackfillRunnerTest {

    @Test
    void runsOnlyTheExplicitlyRequestedBackfillUntilItIsComplete() {
        CompanyInsightBackfillService backfillService = mock(CompanyInsightBackfillService.class);
        when(backfillService.processNextChunk(10L, 42L)).thenReturn(true, true, false);

        CompanyInsightBackfillRunner runner = new CompanyInsightBackfillRunner(backfillService);
        runner.runToCompletion(10L, 42L);

        verify(backfillService, times(3)).processNextChunk(10L, 42L);
    }
}
