package com.example.valueinsoftbackend.ai.audit;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.dto.AiAdminErrorDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminErrorsResponse;
import com.example.valueinsoftbackend.ai.dto.AiAdminToolAuditItemDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminToolAuditResponse;
import com.example.valueinsoftbackend.ai.dto.AiAdminTopQuestionDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminTopQuestionsResponse;
import com.example.valueinsoftbackend.ai.dto.AiAdminUsageCompanyDto;
import com.example.valueinsoftbackend.ai.dto.AiAdminUsageResponse;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AiAdminMonitoringService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AiAdminMonitoringRepository repository;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final AiPermissionService permissionService;
    private final AiProperties aiProperties;

    public AiAdminMonitoringService(AiAdminMonitoringRepository repository,
                                    PlatformAuthorizationService platformAuthorizationService,
                                    AiPermissionService permissionService,
                                    AiProperties aiProperties) {
        this.repository = repository;
        this.platformAuthorizationService = platformAuthorizationService;
        this.permissionService = permissionService;
        this.aiProperties = aiProperties;
    }

    public AiAdminUsageResponse getUsage(Principal principal, LocalDate fromDate, LocalDate toDate, Integer limit) {
        requireAdmin(principal);
        DateRange range = dateRange(fromDate, toDate);
        List<AiAdminUsageCompanyDto> companies = repository.findUsageByCompany(
                range.fromDateTime(),
                range.toDateExclusive(),
                nearLimitThreshold(),
                normalizeLimit(limit)
        );
        long totalRequests = companies.stream().mapToLong(AiAdminUsageCompanyDto::requestCount).sum();
        long totalTokens = companies.stream().mapToLong(AiAdminUsageCompanyDto::totalTokens).sum();
        BigDecimal estimatedCost = companies.stream()
                .map(AiAdminUsageCompanyDto::estimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageLatency = weightedAverageLatency(companies, totalRequests);
        long companiesNearLimit = companies.stream().filter(AiAdminUsageCompanyDto::nearMonthlyTokenLimit).count();
        return new AiAdminUsageResponse(
                Instant.now(),
                range.fromDate(),
                range.toDate(),
                totalRequests,
                totalTokens,
                estimatedCost,
                averageLatency,
                companiesNearLimit,
                companies
        );
    }

    public AiAdminToolAuditResponse getToolAudit(Principal principal, LocalDate fromDate, LocalDate toDate, Integer limit) {
        requireAdmin(principal);
        DateRange range = dateRange(fromDate, toDate);
        List<AiAdminToolAuditItemDto> items = repository.findToolAudit(
                        range.fromDateTime(),
                        range.toDateExclusive(),
                        normalizeLimit(limit)
                ).stream()
                .map(this::maskToolAudit)
                .toList();
        return new AiAdminToolAuditResponse(Instant.now(), range.fromDate(), range.toDate(), items);
    }

    public AiAdminErrorsResponse getErrors(Principal principal, LocalDate fromDate, LocalDate toDate, Integer limit) {
        requireAdmin(principal);
        DateRange range = dateRange(fromDate, toDate);
        List<AiAdminErrorDto> errors = repository.findErrors(
                        range.fromDateTime(),
                        range.toDateExclusive(),
                        normalizeLimit(limit)
                ).stream()
                .map(this::maskError)
                .toList();
        return new AiAdminErrorsResponse(Instant.now(), range.fromDate(), range.toDate(), errors);
    }

    public AiAdminTopQuestionsResponse getTopQuestions(Principal principal, LocalDate fromDate, LocalDate toDate, Integer limit) {
        requireAdmin(principal);
        DateRange range = dateRange(fromDate, toDate);
        List<AiAdminTopQuestionDto> questions = repository.findTopQuestions(
                        range.fromDateTime(),
                        range.toDateExclusive(),
                        normalizeLimit(limit)
                ).stream()
                .map(question -> new AiAdminTopQuestionDto(
                        safeText(question.question(), 180),
                        question.count(),
                        question.lastAskedAt()
                ))
                .toList();
        return new AiAdminTopQuestionsResponse(Instant.now(), range.fromDate(), range.toDate(), questions);
    }

    private void requireAdmin(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        platformAuthorizationService.requirePlatformCapability(principal.getName(), "platform.admin.read");
    }

    private DateRange dateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.minusDays(DEFAULT_DAYS - 1L) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AI_ADMIN_DATE_RANGE", "Invalid date range");
        }
        if (effectiveFrom.isBefore(effectiveTo.minusDays(MAX_DAYS - 1L))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_ADMIN_DATE_RANGE_TOO_LARGE", "Date range is too large");
        }
        return new DateRange(effectiveFrom, effectiveTo, effectiveFrom.atStartOfDay(), effectiveTo.plusDays(1).atStartOfDay());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private long nearLimitThreshold() {
        long limit = aiProperties.getMonthlyTokenLimitDefault();
        return limit <= 0 ? 0 : Math.round(limit * 0.8d);
    }

    private BigDecimal weightedAverageLatency(List<AiAdminUsageCompanyDto> companies, long totalRequests) {
        if (totalRequests <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal weightedTotal = companies.stream()
                .map(company -> company.averageLatencyMs().multiply(BigDecimal.valueOf(company.requestCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weightedTotal.divide(BigDecimal.valueOf(totalRequests), 2, RoundingMode.HALF_UP);
    }

    private AiAdminToolAuditItemDto maskToolAudit(AiAdminToolAuditItemDto item) {
        return new AiAdminToolAuditItemDto(
                item.id(),
                item.conversationId(),
                item.companyId(),
                item.branchId(),
                item.userId(),
                safeText(item.toolName(), 150),
                safeText(item.outputSummary(), 500),
                item.success(),
                safeText(item.errorMessage(), 300),
                item.durationMs(),
                item.createdAt()
        );
    }

    private AiAdminErrorDto maskError(AiAdminErrorDto error) {
        return new AiAdminErrorDto(
                error.id(),
                error.conversationId(),
                error.companyId(),
                error.branchId(),
                error.userId(),
                safeText(error.source(), 150),
                safeText(error.message(), 300),
                error.durationMs(),
                error.createdAt()
        );
    }

    private String safeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String masked = permissionService.maskSensitiveData(value).replaceAll("\\s+", " ").trim();
        return masked.length() <= maxLength ? masked : masked.substring(0, maxLength);
    }

    private record DateRange(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime fromDateTime,
            LocalDateTime toDateExclusive
    ) {
    }
}
