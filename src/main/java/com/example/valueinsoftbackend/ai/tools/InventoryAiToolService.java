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
public class InventoryAiToolService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String TOOL_NAME = "InventoryAiTools";

    private final InventoryAiRepository repository;
    private final AiPermissionService permissionService;
    private final AiToolAuditService auditService;

    public InventoryAiToolService(InventoryAiRepository repository,
                                  AiPermissionService permissionService,
                                  AiToolAuditService auditService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public List<InventoryAiProductDto> getLowStockProducts(AiSecurityContext context,
                                                           UUID conversationId,
                                                           long branchId,
                                                           Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return audited(context, conversationId, branchId, "getLowStockProducts",
                Map.of("branchId", branchId, "limit", safeLimit),
                () -> repository.getLowStockProducts(context.companyId(), branchId, safeLimit));
    }

    public List<InventoryAiProductDto> searchProductByName(AiSecurityContext context,
                                                           UUID conversationId,
                                                           long branchId,
                                                           String productName,
                                                           Integer limit) {
        int safeLimit = normalizeLimit(limit);
        return audited(context, conversationId, branchId, "searchProductByName",
                Map.of("branchId", branchId, "productName", cleanText(productName), "limit", safeLimit),
                () -> {
                    if (cleanText(productName).isBlank()) {
                        return List.of();
                    }
                    return repository.searchProductByName(context.companyId(), branchId, cleanText(productName), safeLimit);
                });
    }

    public Optional<InventoryAiProductDto> getProductByBarcode(AiSecurityContext context,
                                                               UUID conversationId,
                                                               long branchId,
                                                               String barcode) {
        return audited(context, conversationId, branchId, "getProductByBarcode",
                Map.of("branchId", branchId, "barcode", cleanText(barcode)),
                () -> {
                    if (cleanText(barcode).isBlank()) {
                        return Optional.empty();
                    }
                    return repository.getProductByBarcode(context.companyId(), branchId, cleanText(barcode));
                });
    }

    public Optional<InventoryAiProductDto> getProductStock(AiSecurityContext context,
                                                           UUID conversationId,
                                                           long branchId,
                                                           long productId) {
        return audited(context, conversationId, branchId, "getProductStock",
                Map.of("branchId", branchId, "productId", productId),
                () -> repository.getProductStock(context.companyId(), branchId, productId));
    }

    public long countProductsInStock(AiSecurityContext context,
                                     UUID conversationId,
                                     long branchId) {
        return audited(context, conversationId, branchId, "countProductsInStock",
                Map.of("branchId", branchId),
                () -> repository.countProductsInStock(context.companyId(), branchId));
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
            auditService.logToolCall(
                    conversationId,
                    context.companyId(),
                    branchId,
                    context.userId(),
                    methodName,
                    input,
                    summarize(result),
                    true,
                    null,
                    elapsedMs(startedAt)
            );
            return result;
        } catch (RuntimeException exception) {
            auditService.logToolCall(
                    conversationId,
                    context.companyId(),
                    branchId,
                    context.userId(),
                    methodName,
                    input,
                    "Inventory tool failed",
                    false,
                    "Inventory tool failed",
                    elapsedMs(startedAt)
            );
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
            return "Returned " + list.size() + " inventory item(s)";
        }
        if (result instanceof Optional<?> optional) {
            return optional.isPresent() ? "Returned 1 inventory item" : "Returned 0 inventory item(s)";
        }
        if (result instanceof Number number) {
            return "Returned inventory count " + number.longValue();
        }
        return "Inventory tool completed";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
