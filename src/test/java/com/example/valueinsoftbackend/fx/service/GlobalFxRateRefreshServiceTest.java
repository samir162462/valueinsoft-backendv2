package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAuditWriter;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import com.example.valueinsoftbackend.fx.model.FxCompanyProcessingSummary;
import com.example.valueinsoftbackend.fx.model.FxDeepSeekFetchResult;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.example.valueinsoftbackend.fx.model.FxRateSnapshotInsert;
import com.example.valueinsoftbackend.fx.model.FxRefreshResult;
import com.example.valueinsoftbackend.fx.model.FxRefreshTrigger;
import com.example.valueinsoftbackend.fx.model.FxValidationResult;
import com.example.valueinsoftbackend.fx.model.GlobalFxRateSnapshot;
import com.example.valueinsoftbackend.fx.repository.FxSchedulerLockRepository;
import com.example.valueinsoftbackend.fx.repository.GlobalFxRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalFxRateRefreshServiceTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 6, 7);
    private static final String SOURCE_CODE = "EXCHANGE_RATE_API";

    private GlobalFxRateRepository rateRepository;
    private FxSchedulerLockRepository lockRepository;
    private LiveExchangeRateFxRateProvider liveRateProvider;
    private FxRateValidator validator;
    private FxWeekPeriodService weekPeriodService;
    private FxCompanyProcessingService companyProcessingService;
    private PlatformAuthorizationService platformAuthorizationService;
    private DbPlatformAdminAuditWriter auditWriter;
    private GlobalFxRateRefreshService service;

    @BeforeEach
    void setUp() {
        rateRepository = mock(GlobalFxRateRepository.class);
        lockRepository = mock(FxSchedulerLockRepository.class);
        liveRateProvider = mock(LiveExchangeRateFxRateProvider.class);
        validator = mock(FxRateValidator.class);
        weekPeriodService = mock(FxWeekPeriodService.class);
        companyProcessingService = mock(FxCompanyProcessingService.class);
        platformAuthorizationService = mock(PlatformAuthorizationService.class);
        auditWriter = mock(DbPlatformAdminAuditWriter.class);

        when(weekPeriodService.currentWeekStart()).thenReturn(WEEK_START);
        when(weekPeriodService.zoneId()).thenReturn(java.time.ZoneId.of("Africa/Cairo"));
        service = new GlobalFxRateRefreshService(
                rateRepository,
                lockRepository,
                liveRateProvider,
                validator,
                weekPeriodService,
                companyProcessingService,
                properties(),
                platformAuthorizationService,
                auditWriter
        );
    }

    @Test
    void startupExistingValidGlobalRateSkipsProvider() {
        allowLocks();
        when(rateRepository.validRateExists("USD", "EGP")).thenReturn(true);

        FxRefreshResult result = service.initializeMissingRate();

        assertEquals("SKIPPED", result.status());
        assertFalse(result.deepSeekCalled());
        verify(liveRateProvider, never()).fetchRate();
        verify(rateRepository, never()).insertSnapshot(any(FxRateSnapshotInsert.class));
    }

    @Test
    void startupMissingGlobalRateFetchesInitialSnapshotAndProcessesCompanies() {
        allowLocks();
        when(rateRepository.validRateExists("USD", "EGP")).thenReturn(false);
        when(rateRepository.findLatestValid("USD", "EGP")).thenReturn(Optional.empty());
        when(liveRateProvider.fetchRate()).thenReturn(fetchResult());
        when(validator.validate(any(FxRatePayload.class), eq(Optional.empty()))).thenReturn(FxValidationResult.accepted());
        when(rateRepository.insertSnapshot(any(FxRateSnapshotInsert.class))).thenReturn(snapshot(11L));
        FxCompanyProcessingSummary summary = new FxCompanyProcessingSummary(2, 2, 1, 0, 1, 3, 2);
        when(companyProcessingService.processAllEligibleCompanies(any(GlobalFxRateSnapshot.class))).thenReturn(summary);

        FxRefreshResult result = service.initializeMissingRate();

        assertEquals("SUCCESS", result.status());
        assertTrue(result.deepSeekCalled());
        assertEquals(11L, result.snapshotId());
        assertEquals(summary, result.companyProcessingSummary());

        ArgumentCaptor<FxRateSnapshotInsert> insertCaptor = ArgumentCaptor.forClass(FxRateSnapshotInsert.class);
        verify(rateRepository).insertSnapshot(insertCaptor.capture());
        FxRateSnapshotInsert saved = insertCaptor.getValue();
        assertEquals(FxRefreshTrigger.INITIALIZATION, saved.triggerType());
        assertTrue(saved.initialRate());
        assertFalse(saved.scheduledRate());
        assertEquals("VALID", saved.status());
        assertEquals("VALID", saved.validationStatus());
        assertEquals(SOURCE_CODE, saved.sourceCode());
        assertEquals(WEEK_START, saved.weekStartDate());

        ArgumentCaptor<GlobalFxRateSnapshot> processedSnapshotCaptor = ArgumentCaptor.forClass(GlobalFxRateSnapshot.class);
        verify(companyProcessingService).processAllEligibleCompanies(processedSnapshotCaptor.capture());
        assertEquals(11L, processedSnapshotCaptor.getValue().id());
    }

    @Test
    void dailyMissingCurrentRateFetchesScheduledSnapshotOnceAndProcessesCompanies() {
        allowLocks();
        when(rateRepository.scheduledValidRateExistsForDate(eq("USD"), eq("EGP"), any(LocalDate.class), eq(SOURCE_CODE))).thenReturn(false);
        when(rateRepository.findLatestValid("USD", "EGP")).thenReturn(Optional.empty());
        when(liveRateProvider.fetchRate()).thenReturn(fetchResult());
        when(validator.validate(any(FxRatePayload.class), eq(Optional.empty()))).thenReturn(FxValidationResult.accepted());
        when(rateRepository.insertSnapshot(any(FxRateSnapshotInsert.class))).thenReturn(snapshot(33L));
        FxCompanyProcessingSummary summary = new FxCompanyProcessingSummary(3, 2, 2, 0, 0, 4, 3);
        when(companyProcessingService.processAllEligibleCompanies(any(GlobalFxRateSnapshot.class))).thenReturn(summary);

        FxRefreshResult result = service.runDailySchedule();

        assertEquals("SUCCESS", result.status());
        assertTrue(result.deepSeekCalled());
        assertEquals(33L, result.snapshotId());
        assertEquals(summary, result.companyProcessingSummary());
        verify(liveRateProvider, times(1)).fetchRate();

        ArgumentCaptor<FxRateSnapshotInsert> insertCaptor = ArgumentCaptor.forClass(FxRateSnapshotInsert.class);
        verify(rateRepository).insertSnapshot(insertCaptor.capture());
        FxRateSnapshotInsert saved = insertCaptor.getValue();
        assertEquals(FxRefreshTrigger.SCHEDULED, saved.triggerType());
        assertFalse(saved.initialRate());
        assertTrue(saved.scheduledRate());
        assertEquals(WEEK_START, saved.weekStartDate());
    }

    @Test
    void dailyCurrentDayAlreadyProcessedSkipsProvider() {
        allowLocks();
        when(rateRepository.scheduledValidRateExistsForDate(eq("USD"), eq("EGP"), any(LocalDate.class), eq(SOURCE_CODE))).thenReturn(true);

        FxRefreshResult result = service.runDailySchedule();

        assertEquals("SKIPPED", result.status());
        assertFalse(result.deepSeekCalled());
        verify(liveRateProvider, never()).fetchRate();
    }

    @Test
    void dailyDuplicateSnapshotRaceIsSkippedByDatabaseConstraint() {
        allowLocks();
        when(rateRepository.scheduledValidRateExistsForDate(eq("USD"), eq("EGP"), any(LocalDate.class), eq(SOURCE_CODE))).thenReturn(false);
        when(rateRepository.findLatestValid("USD", "EGP")).thenReturn(Optional.empty());
        when(liveRateProvider.fetchRate()).thenReturn(fetchResult());
        when(validator.validate(any(FxRatePayload.class), eq(Optional.empty()))).thenReturn(FxValidationResult.accepted());
        when(rateRepository.insertSnapshot(any(FxRateSnapshotInsert.class))).thenThrow(new DuplicateKeyException("duplicate"));

        FxRefreshResult result = service.runDailySchedule();

        assertEquals("SKIPPED", result.status());
        assertTrue(result.deepSeekCalled());
        verify(companyProcessingService, never()).processAllEligibleCompanies(any(GlobalFxRateSnapshot.class));
    }

    @Test
    void lockContentionSkipsWithoutProviderCall() {
        when(lockRepository.tryAcquire(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        FxRefreshResult result = service.runDailySchedule();

        assertEquals("SKIPPED", result.status());
        assertFalse(result.deepSeekCalled());
        verify(liveRateProvider, never()).fetchRate();
    }

    @Test
    void manualRefreshRequiresPermissionAuditsAndProcessesCompanies() {
        allowLocks();
        User actor = new User(
                1,
                "admin",
                "password",
                "admin@example.com",
                "Admin",
                "User",
                "01000000000",
                "SupportAdmin",
                0,
                0,
                Timestamp.from(Instant.now())
        );
        when(platformAuthorizationService.requirePlatformCapability("principal", "platform.fx.refresh")).thenReturn(actor);
        when(rateRepository.findLatestValid("USD", "EGP")).thenReturn(Optional.empty());
        when(liveRateProvider.fetchRate()).thenReturn(fetchResult());
        when(validator.validate(any(FxRatePayload.class), eq(Optional.empty()))).thenReturn(FxValidationResult.accepted());
        when(rateRepository.insertSnapshot(any(FxRateSnapshotInsert.class))).thenReturn(snapshot(22L));
        when(companyProcessingService.processAllEligibleCompanies(any(GlobalFxRateSnapshot.class))).thenReturn(FxCompanyProcessingSummary.empty());

        FxRefreshResult result = service.manualRefresh("principal", true);

        assertEquals("SUCCESS", result.status());
        assertEquals(22L, result.snapshotId());
        verify(platformAuthorizationService).requirePlatformCapability("principal", "platform.fx.refresh");
        ArgumentCaptor<GlobalFxRateSnapshot> processedSnapshotCaptor = ArgumentCaptor.forClass(GlobalFxRateSnapshot.class);
        verify(companyProcessingService).processAllEligibleCompanies(processedSnapshotCaptor.capture());
        assertEquals(22L, processedSnapshotCaptor.getValue().id());
        verify(auditWriter).createAuditEvent(
                eq("admin"),
                eq("platform.fx.refresh"),
                eq("platform.fx.refresh"),
                isNull(),
                isNull(),
                eq("{\"processCompanies\":true}"),
                contains("\"status\":\"SUCCESS\""),
                eq("success")
        );
    }

    private void allowLocks() {
        when(lockRepository.tryAcquire(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    private FxDeepSeekFetchResult fetchResult() {
        OffsetDateTime now = OffsetDateTime.now();
        return new FxDeepSeekFetchResult(
                new FxRatePayload(
                        "USD",
                        "EGP",
                        new BigDecimal("50.00000000"),
                        "MARKET",
                        LocalDate.of(2026, 6, 8),
                        "ExchangeRate API reference",
                        BigDecimal.ONE
                ),
                "{\"rates\":{\"EGP\":50.00000000}}",
                now,
                now.plusSeconds(1)
        );
    }

    private GlobalFxRateSnapshot snapshot(long id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new GlobalFxRateSnapshot(
                id,
                "USD",
                "EGP",
                WEEK_START,
                LocalDate.of(2026, 6, 8),
                new BigDecimal("50.00000000"),
                "MARKET",
                SOURCE_CODE,
                "ExchangeRate API reference",
                BigDecimal.ONE,
                now,
                now,
                "{\"rates\":{\"EGP\":50.00000000}}",
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
        properties.setLockAtMostMinutes(30);
        return properties;
    }
}
