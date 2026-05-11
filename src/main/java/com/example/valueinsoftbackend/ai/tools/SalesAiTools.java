package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SalesAiTools {

    private final SalesAiToolService toolService;

    public SalesAiTools(SalesAiToolService toolService) {
        this.toolService = toolService;
    }

    public SalesAiSummaryDto getTodaySalesSummary(AiSecurityContext context, UUID conversationId, long branchId) {
        return toolService.getTodaySalesSummary(context, conversationId, branchId);
    }

    public SalesAiSummaryDto getSalesSummaryByDateRange(AiSecurityContext context,
                                                        UUID conversationId,
                                                        long branchId,
                                                        AiToolDateRange range) {
        return toolService.getSalesSummaryByDateRange(context, conversationId, branchId, range);
    }

    public List<SalesAiTopProductDto> getTopSellingProducts(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            AiToolDateRange range,
                                                            Integer limit) {
        return toolService.getTopSellingProducts(context, conversationId, branchId, range, limit);
    }

    public List<SalesAiCashierDto> getSalesByCashier(AiSecurityContext context,
                                                     UUID conversationId,
                                                     long branchId,
                                                     AiToolDateRange range) {
        return toolService.getSalesByCashier(context, conversationId, branchId, range);
    }

    public List<PaymentBreakdownDto> getPaymentBreakdown(AiSecurityContext context,
                                                         UUID conversationId,
                                                         long branchId,
                                                         AiToolDateRange range) {
        return toolService.getPaymentBreakdown(context, conversationId, branchId, range);
    }
}
