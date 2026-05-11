package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SupplierAiTools {

    private final SupplierAiToolService toolService;

    public SupplierAiTools(SupplierAiToolService toolService) {
        this.toolService = toolService;
    }

    public Optional<SupplierAiDto> getSupplierBalance(AiSecurityContext context,
                                                      UUID conversationId,
                                                      long branchId,
                                                      String supplierName) {
        return toolService.getSupplierBalance(context, conversationId, branchId, supplierName);
    }

    public List<SupplierInvoiceAiDto> getPendingSupplierInvoices(AiSecurityContext context,
                                                                 UUID conversationId,
                                                                 long branchId,
                                                                 String supplierName,
                                                                 Integer limit) {
        return toolService.getPendingSupplierInvoices(context, conversationId, branchId, supplierName, limit);
    }

    public List<SupplierAiDto> getTopSuppliersByPayable(AiSecurityContext context,
                                                        UUID conversationId,
                                                        long branchId,
                                                        Integer limit) {
        return toolService.getTopSuppliersByPayable(context, conversationId, branchId, limit);
    }
}
