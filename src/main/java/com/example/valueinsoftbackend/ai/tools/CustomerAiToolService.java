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
public class CustomerAiToolService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_ORDER_LIMIT = 10;
    private static final String TOOL_NAME = "CustomerAiTools";

    private final CustomerAiRepository repository;
    private final AiPermissionService permissionService;
    private final AiToolAuditService auditService;

    public CustomerAiToolService(CustomerAiRepository repository,
                                 AiPermissionService permissionService,
                                 AiToolAuditService auditService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public List<CustomerAiDto> searchCustomer(AiSecurityContext context,
                                              UUID conversationId,
                                              long branchId,
                                              String customerNameOrPhone) {
        return audited(context, conversationId, branchId, "searchCustomer",
                Map.of("branchId", branchId, "customerNameOrPhone", cleanText(customerNameOrPhone)),
                () -> repository.searchCustomer(context.companyId(), branchId, cleanText(customerNameOrPhone), DEFAULT_LIMIT));
    }

    public Optional<CustomerBalanceAiDto> getCustomerBalance(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            long customerId) {
        return audited(context, conversationId, branchId, "getCustomerBalance",
                Map.of("branchId", branchId, "customerId", customerId),
                () -> repository.getCustomerBalance(context.companyId(), branchId, customerId));
    }

    public List<CustomerOrderAiDto> getCustomerLastOrders(AiSecurityContext context,
                                                          UUID conversationId,
                                                          long branchId,
                                                          long customerId,
                                                          Integer limit) {
        int safeLimit = normalizeOrderLimit(limit);
        return audited(context, conversationId, branchId, "getCustomerLastOrders",
                Map.of("branchId", branchId, "customerId", customerId, "limit", safeLimit),
                () -> repository.getCustomerLastOrders(context.companyId(), branchId, customerId, safeLimit));
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
                    methodName, input, "Customer tool failed", false, "Customer tool failed", elapsedMs(startedAt));
            throw exception;
        }
    }

    private int normalizeOrderLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_ORDER_LIMIT);
    }

    private String summarize(Object result) {
        if (result instanceof List<?> list) {
            return "Returned " + list.size() + " customer row(s)";
        }
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? "Returned 1 customer row" : "Returned 0 customer rows";
        }
        return "Customer tool completed";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
