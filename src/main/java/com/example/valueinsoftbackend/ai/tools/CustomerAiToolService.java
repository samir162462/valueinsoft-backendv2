package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.audit.AiToolAuditService;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.ai.tools.AiToolDateRange;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorFilter;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorOverview;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorPage;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorProfile;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorRow;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerPreferenceSummary;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerProductAffinity;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRetentionCohort;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegment;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegmentSummary;
import com.example.valueinsoftbackend.customerbehavior.security.CustomerBehaviorSecurityService;
import com.example.valueinsoftbackend.customerbehavior.service.CustomerBehaviorService;
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
    private final CustomerBehaviorService customerBehaviorService;
    private final CustomerBehaviorSecurityService customerBehaviorSecurityService;

    public CustomerAiToolService(CustomerAiRepository repository,
                                 AiPermissionService permissionService,
                                 AiToolAuditService auditService,
                                 CustomerBehaviorService customerBehaviorService,
                                 CustomerBehaviorSecurityService customerBehaviorSecurityService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.customerBehaviorService = customerBehaviorService;
        this.customerBehaviorSecurityService = customerBehaviorSecurityService;
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

    public List<CustomerSegmentSummary> getCustomerSegments(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            AiToolDateRange range) {
        CustomerBehaviorFilter filter = behaviorFilter(branchId, range, null, 0, 25);
        return auditedBehavior(context, conversationId, branchId, "getCustomerSegments",
                input(branchId, range, null, null),
                () -> customerBehaviorService.getOverview(context, filter).segments());
    }

    public CustomerBehaviorPage<CustomerBehaviorRow> getAtRiskCustomers(AiSecurityContext context,
                                                                        UUID conversationId,
                                                                        long branchId,
                                                                        AiToolDateRange range,
                                                                        Integer limit) {
        int safeLimit = normalizeBehaviorLimit(limit);
        CustomerBehaviorFilter filter = behaviorFilter(branchId, range, CustomerSegment.AT_RISK, 0, safeLimit);
        return auditedBehavior(context, conversationId, branchId, "getAtRiskCustomers",
                input(branchId, range, safeLimit, CustomerSegment.AT_RISK),
                () -> customerBehaviorService.searchCustomers(context, filter));
    }

    public CustomerPreferenceSummary getCustomerPreferences(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            AiToolDateRange range,
                                                            Integer limit) {
        int safeLimit = normalizeBehaviorLimit(limit);
        CustomerBehaviorFilter filter = behaviorFilter(branchId, range, null, 0, safeLimit);
        return auditedBehavior(context, conversationId, branchId, "getCustomerPreferences",
                input(branchId, range, safeLimit, null),
                () -> customerBehaviorService.getPreferences(context, filter, safeLimit));
    }

    public CustomerBehaviorProfile getCustomerPurchasePattern(AiSecurityContext context,
                                                              UUID conversationId,
                                                              long branchId,
                                                              long customerId,
                                                              AiToolDateRange range) {
        CustomerBehaviorFilter filter = behaviorFilter(branchId, range, null, 0, 1);
        return auditedBehavior(context, conversationId, branchId, "getCustomerPurchasePattern",
                Map.of("branchId", branchId, "customerId", customerId, "fromDate", range.fromDate().toString(), "toDate", range.toDate().toString()),
                () -> customerBehaviorService.getCustomerProfile(context, customerId, filter));
    }

    public List<CustomerProductAffinity> getCustomerAffinityProducts(AiSecurityContext context,
                                                                     UUID conversationId,
                                                                     long branchId,
                                                                     AiToolDateRange range) {
        CustomerBehaviorFilter filter = behaviorFilter(branchId, range, null, 0, 25);
        return auditedBehavior(context, conversationId, branchId, "getCustomerAffinityProducts",
                input(branchId, range, null, null),
                () -> customerBehaviorService.getAffinity(context, filter));
    }

    public List<CustomerRetentionCohort> getCustomerRetentionCohorts(AiSecurityContext context,
                                                                     UUID conversationId,
                                                                     long branchId,
                                                                     AiToolDateRange range) {
        CustomerBehaviorFilter filter = behaviorFilter(branchId, range, null, 0, 25);
        return auditedBehavior(context, conversationId, branchId, "getCustomerRetentionCohorts",
                input(branchId, range, null, null),
                () -> customerBehaviorService.getCohorts(context, filter));
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

    private <T> T auditedBehavior(AiSecurityContext context,
                                  UUID conversationId,
                                  long branchId,
                                  String methodName,
                                  Object input,
                                  Supplier<T> supplier) {
        long startedAt = System.nanoTime();
        permissionService.validateToolAccess(TOOL_NAME, context, branchId);
        customerBehaviorSecurityService.authorizeAi(context, behaviorFilter(branchId, null, null, 0, 25));
        try {
            T result = supplier.get();
            auditService.logToolCall(conversationId, context.companyId(), branchId, context.userId(),
                    methodName, input, summarize(result), true, null, elapsedMs(startedAt));
            return result;
        } catch (RuntimeException exception) {
            auditService.logToolCall(conversationId, context.companyId(), branchId, context.userId(),
                    methodName, input, "Customer behavior tool failed", false, "Customer behavior tool failed", elapsedMs(startedAt));
            throw exception;
        }
    }

    private int normalizeOrderLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_ORDER_LIMIT);
    }

    private int normalizeBehaviorLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 25);
    }

    private CustomerBehaviorFilter behaviorFilter(long branchId,
                                                  AiToolDateRange range,
                                                  CustomerSegment segment,
                                                  int page,
                                                  int pageSize) {
        return new CustomerBehaviorFilter(
                List.of(Math.toIntExact(branchId)),
                range == null ? null : range.fromDate(),
                range == null ? null : range.toDate(),
                segment,
                null,
                null,
                page,
                pageSize,
                segment == CustomerSegment.AT_RISK ? "daysInactive" : "totalSpend",
                "desc"
        );
    }

    private Map<String, Object> input(long branchId, AiToolDateRange range, Integer limit, CustomerSegment segment) {
        return Map.of(
                "branchId", branchId,
                "fromDate", range == null ? "" : range.fromDate().toString(),
                "toDate", range == null ? "" : range.toDate().toString(),
                "limit", limit == null ? "" : limit,
                "segment", segment == null ? "" : segment.name()
        );
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
