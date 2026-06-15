package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAuditWriter;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class GlobalFxRateRefreshService {

    private static final String INITIALIZATION_LOCK = "GLOBAL_USD_EGP_INITIALIZATION";
    private static final String DAILY_REFRESH_LOCK = "GLOBAL_USD_EGP_DAILY_REFRESH";
    private static final String MANUAL_REFRESH_LOCK = "GLOBAL_USD_EGP_MANUAL_REFRESH";
    private static final String COMPANY_PROCESSING_LOCK_PREFIX = "GLOBAL_USD_EGP_COMPANY_PROCESSING_";

    private final GlobalFxRateRepository rateRepository;
    private final FxSchedulerLockRepository lockRepository;
    private final LiveExchangeRateFxRateProvider liveExchangeRateFxRateProvider;
    private final FxRateValidator validator;
    private final FxWeekPeriodService weekPeriodService;
    private final FxCompanyProcessingService companyProcessingService;
    private final FxDeepSeekProperties properties;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbPlatformAdminAuditWriter auditWriter;

    public GlobalFxRateRefreshService(GlobalFxRateRepository rateRepository,
                                      FxSchedulerLockRepository lockRepository,
                                      LiveExchangeRateFxRateProvider liveExchangeRateFxRateProvider,
                                      FxRateValidator validator,
                                      FxWeekPeriodService weekPeriodService,
                                      FxCompanyProcessingService companyProcessingService,
                                      FxDeepSeekProperties properties,
                                      PlatformAuthorizationService platformAuthorizationService,
                                      DbPlatformAdminAuditWriter auditWriter) {
        this.rateRepository = rateRepository;
        this.lockRepository = lockRepository;
        this.liveExchangeRateFxRateProvider = liveExchangeRateFxRateProvider;
        this.validator = validator;
        this.weekPeriodService = weekPeriodService;
        this.companyProcessingService = companyProcessingService;
        this.properties = properties;
        this.platformAuthorizationService = platformAuthorizationService;
        this.auditWriter = auditWriter;
    }

    public FxRefreshResult initializeMissingRate() {
        if (!properties.getInitialization().isEnabled()) {
            return FxRefreshResult.skipped("FX rate initialization is disabled.", weekPeriodService.currentWeekStart(), false);
        }
        return withLock(
                INITIALIZATION_LOCK,
                () -> {
                    String baseCurrency = baseCurrency();
                    String targetCurrency = targetCurrency();
                    if (rateRepository.validRateExists(baseCurrency, targetCurrency)) {
                        log.info("Initial {}/{} rate already exists; startup FX provider call skipped", baseCurrency, targetCurrency);
                        return FxRefreshResult.skipped("Initial USD/EGP rate already exists.", weekPeriodService.currentWeekStart(), false);
                    }
                    return refresh(FxRefreshTrigger.INITIALIZATION, null, true);
                },
                () -> FxRefreshResult.skipped("Another instance is initializing the global USD/EGP rate.", weekPeriodService.currentWeekStart(), false)
        );
    }

    public FxRefreshResult runDailySchedule() {
        if (!properties.getSchedule().isEnabled()) {
            return FxRefreshResult.skipped("FX daily schedule is disabled.", weekPeriodService.currentWeekStart(), false);
        }
        return withLock(
                DAILY_REFRESH_LOCK,
                () -> {
                    LocalDate today = LocalDate.now(weekPeriodService.zoneId());
                    if (rateRepository.scheduledValidRateExistsForDate(baseCurrency(), targetCurrency(), today, sourceCode())) {
                        log.info("Daily {}/{} rate already exists for effectiveDate={}", baseCurrency(), targetCurrency(), today);
                        return FxRefreshResult.skipped("Daily USD/EGP rate already exists for today.", weekPeriodService.currentWeekStart(), false);
                    }
                    return refresh(FxRefreshTrigger.SCHEDULED, null, true);
                },
                () -> FxRefreshResult.skipped("Another instance is refreshing the daily global USD/EGP rate.", weekPeriodService.currentWeekStart(), false)
        );
    }

    public FxRefreshResult manualRefresh(String authenticatedName, boolean processCompanies) {
        User actor = platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.fx.refresh");
        FxRefreshResult result = withLock(
                MANUAL_REFRESH_LOCK,
                () -> refresh(FxRefreshTrigger.MANUAL, actor.getUserName(), processCompanies),
                () -> FxRefreshResult.skipped("Another global USD/EGP manual refresh is already running.", weekPeriodService.currentWeekStart(), false)
        );
        auditManualRefresh(actor.getUserName(), result, processCompanies);
        if ("FAILED".equals(result.status())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GLOBAL_FX_RATE_REFRESH_FAILED", result.message());
        }
        if ("REJECTED".equals(result.status())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "GLOBAL_FX_RATE_REJECTED", result.message());
        }
        return result;
    }

    public Optional<GlobalFxRateSnapshot> latestValidForAuthenticatedUser(String authenticatedName) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.fx.read");
        return rateRepository.findLatestValid(baseCurrency(), targetCurrency());
    }

    FxRefreshResult refresh(FxRefreshTrigger triggerType, String actorName, boolean processCompanies) {
        LocalDate weekStart = weekPeriodService.currentWeekStart();
        Optional<GlobalFxRateSnapshot> previousValidRate = rateRepository.findLatestValid(baseCurrency(), targetCurrency());
        FxDeepSeekFetchResult fetchResult;
        try {
            fetchResult = liveExchangeRateFxRateProvider.fetchRate();
        } catch (FxDeepSeekRateException exception) {
            GlobalFxRateSnapshot attempt = recordProviderFailure(triggerType, weekStart, exception);
            log.warn("Global FX rate retrieval failed trigger={} snapshotId={} message={}",
                    triggerType, attempt.id(), exception.getMessage());
            return new FxRefreshResult(
                    exception.isTransientFailure() ? "FAILED" : "REJECTED",
                    exception.getMessage(),
                    attempt.id(),
                    weekStart,
                    true,
                    FxCompanyProcessingSummary.empty()
            );
        }

        FxValidationResult validation = validator.validate(fetchResult.payload(), previousValidRate);
        if (!validation.valid()) {
            GlobalFxRateSnapshot rejected = recordRejectedAttempt(triggerType, weekStart, fetchResult, validation.message());
            log.warn("Global FX rate rejected trigger={} snapshotId={} reason={}", triggerType, rejected.id(), validation.message());
            return new FxRefreshResult("REJECTED", validation.message(), rejected.id(), weekStart, true, FxCompanyProcessingSummary.empty());
        }

        GlobalFxRateSnapshot snapshot;
        try {
            snapshot = recordValidSnapshot(triggerType, weekStart, fetchResult);
        } catch (DuplicateKeyException exception) {
            log.info("Duplicate scheduled global FX snapshot prevented by database constraint trigger={} weekStart={}", triggerType, weekStart);
            return FxRefreshResult.skipped("Daily USD/EGP rate already exists for today.", weekStart, true);
        }

        FxCompanyProcessingSummary summary = FxCompanyProcessingSummary.empty();
        if (processCompanies) {
            summary = processCompaniesForSnapshot(snapshot);
        }

        log.info("Global FX rate refreshed trigger={} snapshotId={} companySummary={}", triggerType, snapshot.id(), summary);
        return new FxRefreshResult(
                "SUCCESS",
                "Global USD/EGP rate refreshed.",
                snapshot.id(),
                weekStart,
                true,
                summary
        );
    }

    private FxCompanyProcessingSummary processCompaniesForSnapshot(GlobalFxRateSnapshot snapshot) {
        String lockName = COMPANY_PROCESSING_LOCK_PREFIX + snapshot.id();
        return withLock(
                lockName,
                () -> companyProcessingService.processAllEligibleCompanies(snapshot),
                () -> {
                    log.info("Company processing already running for global FX snapshot {}", snapshot.id());
                    return FxCompanyProcessingSummary.empty();
                }
        );
    }

    private GlobalFxRateSnapshot recordValidSnapshot(FxRefreshTrigger triggerType,
                                                     LocalDate weekStart,
                                                     FxDeepSeekFetchResult fetchResult) {
        FxRatePayload payload = fetchResult.payload();
        return rateRepository.insertSnapshot(new FxRateSnapshotInsert(
                currency(payload.baseCurrency()),
                currency(payload.targetCurrency()),
                weekStart,
                payload.effectiveDate(),
                payload.rate(),
                blankToDefault(payload.rateType(), "REFERENCE"),
                sourceCode(),
                payload.sourceDescription(),
                payload.confidence(),
                fetchResult.requestTimestamp(),
                fetchResult.responseTimestamp(),
                fetchResult.rawResponse(),
                "VALID",
                "VALID",
                "VALID",
                triggerType == FxRefreshTrigger.INITIALIZATION,
                triggerType == FxRefreshTrigger.SCHEDULED,
                triggerType
        ));
    }

    private GlobalFxRateSnapshot recordRejectedAttempt(FxRefreshTrigger triggerType,
                                                       LocalDate weekStart,
                                                       FxDeepSeekFetchResult fetchResult,
                                                       String reason) {
        FxRatePayload payload = fetchResult.payload();
        return rateRepository.insertSnapshot(new FxRateSnapshotInsert(
                currency(firstNonBlank(payload.baseCurrency(), baseCurrency())),
                currency(firstNonBlank(payload.targetCurrency(), targetCurrency())),
                weekStart,
                payload.effectiveDate(),
                payload.rate(),
                blankToDefault(payload.rateType(), "REFERENCE"),
                sourceCode(),
                payload.sourceDescription(),
                payload.confidence(),
                fetchResult.requestTimestamp(),
                fetchResult.responseTimestamp(),
                fetchResult.rawResponse(),
                "REJECTED",
                "REJECTED",
                truncate(reason),
                triggerType == FxRefreshTrigger.INITIALIZATION,
                triggerType == FxRefreshTrigger.SCHEDULED,
                triggerType
        ));
    }

    private GlobalFxRateSnapshot recordProviderFailure(FxRefreshTrigger triggerType,
                                                       LocalDate weekStart,
                                                       FxDeepSeekRateException exception) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean rejected = exception.getRawResponse() != null && !exception.isTransientFailure();
        return rateRepository.insertSnapshot(new FxRateSnapshotInsert(
                baseCurrency(),
                targetCurrency(),
                weekStart,
                null,
                null,
                "REFERENCE",
                sourceCode(),
                "Live FX provider retrieval attempt",
                null,
                now,
                now,
                exception.getRawResponse(),
                rejected ? "REJECTED" : "FAILED",
                rejected ? "REJECTED" : "FAILED",
                truncate(exception.getMessage()),
                triggerType == FxRefreshTrigger.INITIALIZATION,
                triggerType == FxRefreshTrigger.SCHEDULED,
                triggerType
        ));
    }

    private <T> T withLock(String lockName, Supplier<T> work, Supplier<T> notAcquired) {
        String lockedBy = lockOwner();
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.getLockAtMostMinutes()));
        if (!lockRepository.tryAcquire(lockName, lockedBy, ttl)) {
            return notAcquired.get();
        }
        try {
            return work.get();
        } finally {
            lockRepository.release(lockName, lockedBy);
        }
    }

    private void auditManualRefresh(String actorName, FxRefreshResult result, boolean processCompanies) {
        auditWriter.createAuditEvent(
                actorName,
                "platform.fx.refresh",
                "platform.fx.refresh",
                null,
                null,
                "{\"processCompanies\":" + processCompanies + "}",
                "{\"status\":\"" + json(result.status()) + "\",\"snapshotId\":" + (result.snapshotId() == null ? "null" : result.snapshotId()) + "}",
                auditStatus(result.status())
        );
    }

    private String auditStatus(String resultStatus) {
        return switch (resultStatus) {
            case "SUCCESS", "SKIPPED" -> "success";
            case "REJECTED" -> "rejected";
            default -> "failed";
        };
    }

    private String lockOwner() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + UUID.randomUUID();
        } catch (Exception ignored) {
            return "valueinsoft:" + UUID.randomUUID();
        }
    }

    private String baseCurrency() {
        return currency(properties.getBaseCurrency());
    }

    private String targetCurrency() {
        return currency(properties.getTargetCurrency());
    }

    private String sourceCode() {
        return blankToDefault(properties.getSourceCode(), "EXCHANGE_RATE_API").toUpperCase(Locale.ROOT);
    }

    private String currency(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown global FX refresh failure";
        }
        String normalized = value.trim().replace("\n", " ").replace("\r", " ");
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
