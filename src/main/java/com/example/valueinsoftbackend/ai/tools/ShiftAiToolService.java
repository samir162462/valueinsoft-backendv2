package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.audit.AiToolAuditService;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ShiftAiToolService {

    private static final String TOOL_NAME = "ShiftAiTools";

    private final ShiftAiRepository repository;
    private final AiPermissionService permissionService;
    private final AiToolAuditService auditService;

    public ShiftAiToolService(ShiftAiRepository repository,
                              AiPermissionService permissionService,
                              AiToolAuditService auditService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public Optional<ShiftAiSummaryDto> getCurrentShiftSummary(AiSecurityContext context,
                                                              UUID conversationId,
                                                              long branchId) {
        return audited(context, conversationId, branchId, "getCurrentShiftSummary",
                Map.of("branchId", branchId),
                () -> repository.getCurrentShiftSummary(context.companyId(), branchId));
    }

    public Optional<ShiftAiSummaryDto> getOpenShiftStatus(AiSecurityContext context,
                                                          UUID conversationId,
                                                          long branchId) {
        return getCurrentShiftSummary(context, conversationId, branchId);
    }

    public List<PaymentBreakdownDto> getPaymentBreakdown(AiSecurityContext context,
                                                         UUID conversationId,
                                                         long branchId,
                                                         AiToolDateRange range) {
        return audited(context, conversationId, branchId, "getPaymentBreakdown",
                Map.of("branchId", branchId, "fromDate", range.fromDate().toString(), "toDate", range.toDate().toString()),
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
                    methodName, input, "Shift tool failed", false, "Shift tool failed", elapsedMs(startedAt));
            throw exception;
        }
    }

    private String summarize(Object result) {
        if (result instanceof List<?> list) {
            return "Returned " + list.size() + " shift row(s)";
        }
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? "Returned current shift" : "Returned no current shift";
        }
        return "Shift tool completed";
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
