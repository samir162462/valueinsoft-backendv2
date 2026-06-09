package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.fx.model.FxCompanyConfig;
import com.example.valueinsoftbackend.fx.model.FxCompanyProcessingSummary;
import com.example.valueinsoftbackend.fx.model.FxRefreshTrigger;
import com.example.valueinsoftbackend.fx.model.GlobalFxRateSnapshot;
import com.example.valueinsoftbackend.fx.repository.FxCompanyProcessingRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.DynamicPricingPolicyRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PriceRecommendationRunRepository;
import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingMetricsRepository;
import com.example.valueinsoftbackend.pricing.dynamic.service.PriceRecommendationCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxCompanyProcessingServiceTest {

    private FxCompanyProcessingRepository repository;
    private FxCompanyProcessingService service;

    @BeforeEach
    void setUp() {
        repository = mock(FxCompanyProcessingRepository.class);
        service = new FxCompanyProcessingService(
                repository,
                mock(PricingMetricsRepository.class),
                mock(DynamicPricingPolicyRepository.class),
                mock(PriceRecommendationRunRepository.class),
                mock(PriceRecommendationCalculator.class),
                properties(),
                new NoOpTransactionManager()
        );
    }

    @Test
    void effectivePricingRateAppliesCompanySafetyBuffer() {
        BigDecimal effectiveRate = service.effectivePricingRate(
                new BigDecimal("50.00000000"),
                new BigDecimal("3.0000")
        );

        assertEquals(new BigDecimal("51.50000000"), effectiveRate);
    }

    @Test
    void companyFailureIsRecordedAndDoesNotStopOtherCompanies() {
        FxCompanyConfig skippedCompany = new FxCompanyConfig(1, new BigDecimal("0.0000"), "REFERENCE", 1);
        FxCompanyConfig failedCompany = new FxCompanyConfig(2, new BigDecimal("3.0000"), "REFERENCE", 1);
        GlobalFxRateSnapshot snapshot = snapshot(77L);
        when(repository.countActiveCompanies()).thenReturn(2);
        when(repository.findActiveFxEnabledCompanies()).thenReturn(List.of(skippedCompany, failedCompany));
        when(repository.findActiveBranchIds(1)).thenReturn(List.of());
        when(repository.findActiveBranchIds(2)).thenThrow(new IllegalStateException("branch lookup failed"));

        FxCompanyProcessingSummary summary = service.processAllEligibleCompanies(snapshot);

        assertEquals(2, summary.totalActiveCompanies());
        assertEquals(2, summary.eligibleCompanies());
        assertEquals(0, summary.successfullyProcessedCompanies());
        assertEquals(1, summary.failedCompanies());
        assertEquals(1, summary.skippedCompanies());

        verify(repository).upsertCompanyEffectiveRate(77L, skippedCompany, new BigDecimal("50.00000000"));
        verify(repository).upsertCompanyEffectiveRate(77L, failedCompany, new BigDecimal("51.50000000"));
        verify(repository).recordProcessingResult(
                eq(77L),
                eq(1),
                eq("SKIPPED"),
                eq(new BigDecimal("0.0000")),
                eq(new BigDecimal("50.00000000")),
                eq(0),
                eq(0),
                eq(0),
                eq("NO_ACTIVE_BRANCHES"),
                isNull()
        );
        verify(repository).recordProcessingResult(
                eq(77L),
                eq(2),
                eq("FAILED"),
                eq(new BigDecimal("3.0000")),
                isNull(),
                eq(0),
                eq(0),
                eq(0),
                isNull(),
                contains("branch lookup failed")
        );
    }

    private GlobalFxRateSnapshot snapshot(long id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new GlobalFxRateSnapshot(
                id,
                "USD",
                "EGP",
                LocalDate.of(2026, 6, 7),
                LocalDate.of(2026, 6, 8),
                new BigDecimal("50.00000000"),
                "REFERENCE",
                "DEEPSEEK",
                "DeepSeek reference",
                new BigDecimal("0.9000"),
                now,
                now,
                "{}",
                "VALID",
                "VALID",
                "VALID",
                false,
                true,
                FxRefreshTrigger.SCHEDULED,
                now,
                now
        );
    }

    private FxDeepSeekProperties properties() {
        FxDeepSeekProperties properties = new FxDeepSeekProperties();
        properties.setCompanyBatchSize(1);
        properties.setRecommendationMaxProducts(100);
        properties.setRecommendationMetricsWindowDays(30);
        return properties;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
