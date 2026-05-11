package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.audit.AiToolAuditService;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SalesAiToolService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String TOOL_NAME = "SalesAiTools";

    private final SalesAiRepository repository;
    private final AiPermissionService permissionService;
    private final AiToolAuditService auditService;

    public SalesAiToolService(SalesAiRepository repository,
                              AiPermissionService permissionService,
                              AiToolAuditService auditService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public SalesAiSummaryDto getTodaySalesSummary(AiSecurityContext context, UUID conversationId, long branchId) {
        return getSalesSummaryByDateRange(context, conversationId, branchId, AiToolDateRange.today());
    }

    public SalesAiSummaryDto getSalesSummaryByDateRange(AiSecurityContext context,
                                                        UUID conversationId,
                                                        long branchId,
                                                        AiToolDateRange range) {
        return audited(context, conversationId, branchId, "getSalesSummaryByDateRange",
                input(branchId, range, null),
                () -> repository.getSalesSummary(context.companyId(), branchId, range.fromDate(), range.toDate()));
    }

    public List<SalesAiTopProductDto> getTopSellingProducts(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            AiToolDateRange range,
                                                            Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return audited(context, conversationId, branchId, "getTopSellingProducts",
                input(branchId, range, safeLimit),
                () -> repository.getTopSellingProducts(context.companyId(), branchId, range.fromDate(), range.toDate(), safeLimit));
    }

    public List<SalesAiCashierDto> getSalesByCashier(AiSecurityContext context,
                                                     UUID conversationId,
                                                     long branchId,
                                                     AiToolDateRange range) {
        return audited(context, conversationId, branchId, "getSalesByCashier",
                input(branchId, range, null),
                () -> repository.getSalesByCashier(context.companyId(), branchId, range.fromDate(), range.toDate()));
    }

    public List<PaymentBreakdownDto> getPaymentBreakdown(AiSecurityContext context,
                                                         UUID conversationId,
                                                         long branchId,
                                                         AiToolDateRange range) {
        return audited(context, conversationId, branchId, "getPaymentBreakdown",
                input(branchId, range, null),
                () -> repository.getPaymentBreakdown(context.companyId(), branchId, range.fromDate(), range.toDate()));
    }

    private <T> T audited(AiSecurityContext context,
                          UUID conversationId,
                          long branchId,
                          String methodName,
                          Object input,
                          Supplier<T> supplier) {
        long startedAt = System.nanoTime();
        permissionService.validateToolAccess(TOOL_NAME, context, branchId);
        try {
            T result = supplier.get();
            auditService.logToolCall(conversationId, context.companyId(), branchId, context.userId(),
                    methodName, input, summarize(result), true, null, elapsedMs(startedAt));
            return result;
        } catch (RuntimeException exception) {
            auditService.logToolCall(conversationId, context.companyId(), branchId, context.userId(),
                    methodName, input, "Sales tool failed", false, "Sales tool failed", elapsedMs(startedAt));
            throw exception;
        }
    }

    private Map<String, Object> input(long branchId, AiToolDateRange range, Integer limit) {
        return Map.of(
                "branchId", branchId,
                "fromDate", range.fromDate().toString(),
                "toDate", range.toDate().toString(),
                "limit", limit == null ? "" : limit
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String summarize(Object result) {
        if (result instanceof List<?> list) {
            return "Returned " + list.size() + " sales row(s)";
        }
        return "Returned sales summary";
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
