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
public class SupplierAiToolService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String TOOL_NAME = "SupplierAiTools";

    private final SupplierAiRepository repository;
    private final AiPermissionService permissionService;
    private final AiToolAuditService auditService;

    public SupplierAiToolService(SupplierAiRepository repository,
                                 AiPermissionService permissionService,
                                 AiToolAuditService auditService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public Optional<SupplierAiDto> getSupplierBalance(AiSecurityContext context,
                                                      UUID conversationId,
                                                      long branchId,
                                                      String supplierName) {
        return audited(context, conversationId, branchId, "getSupplierBalance",
                Map.of("branchId", branchId, "supplierName", cleanText(supplierName)),
                () -> repository.getSupplierBalance(context.companyId(), branchId, cleanText(supplierName)));
    }

    public List<SupplierInvoiceAiDto> getPendingSupplierInvoices(AiSecurityContext context,
                                                                 UUID conversationId,
                                                                 long branchId,
                                                                 String supplierName,
                                                                 Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return audited(context, conversationId, branchId, "getPendingSupplierInvoices",
                Map.of("branchId", branchId, "supplierName", cleanText(supplierName), "limit", safeLimit),
                () -> repository.getPendingSupplierInvoices(context.companyId(), branchId, cleanText(supplierName), safeLimit));
    }

    public List<SupplierAiDto> getTopSuppliersByPayable(AiSecurityContext context,
                                                        UUID conversationId,
                                                        long branchId,
                                                        Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return audited(context, conversationId, branchId, "getTopSuppliersByPayable",
                Map.of("branchId", branchId, "limit", safeLimit),
                () -> repository.getTopSuppliersByPayable(context.companyId(), branchId, safeLimit));
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
                    methodName, input, "Supplier tool failed", false, "Supplier tool failed", elapsedMs(startedAt));
            throw exception;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String summarize(Object result) {
        if (result instanceof List<?> list) {
            return "Returned " + list.size() + " supplier row(s)";
        }
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? "Returned 1 supplier row" : "Returned 0 supplier rows";
        }
        return "Supplier tool completed";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
