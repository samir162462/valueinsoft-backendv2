package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class InventoryAiTools {

    private final InventoryAiToolService toolService;

    public InventoryAiTools(InventoryAiToolService toolService) {
        this.toolService = toolService;
    }

    public List<InventoryAiProductDto> getLowStockProducts(AiSecurityContext context,
                                                           UUID conversationId,
                                                           long branchId,
                                                           Integer limit) {
        return toolService.getLowStockProducts(context, conversationId, branchId, limit);
    }

    public List<InventoryAiProductDto> searchProductByName(AiSecurityContext context,
                                                           UUID conversationId,
                                                           long branchId,
                                                           String productName,
                                                           Integer limit) {
        return toolService.searchProductByName(context, conversationId, branchId, productName, limit);
    }

    public Optional<InventoryAiProductDto> getProductByBarcode(AiSecurityContext context,
                                                               UUID conversationId,
                                                               long branchId,
                                                               String barcode) {
        return toolService.getProductByBarcode(context, conversationId, branchId, barcode);
    }

    public Optional<InventoryAiProductDto> getProductStock(AiSecurityContext context,
                                                           UUID conversationId,
                                                           long branchId,
                                                           long productId) {
        return toolService.getProductStock(context, conversationId, branchId, productId);
    }

    public long countProductsInStock(AiSecurityContext context,
                                     UUID conversationId,
                                     long branchId) {
        return toolService.countProductsInStock(context, conversationId, branchId);
    }
}
