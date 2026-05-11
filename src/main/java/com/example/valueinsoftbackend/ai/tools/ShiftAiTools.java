package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ShiftAiTools {

    private final ShiftAiToolService toolService;

    public ShiftAiTools(ShiftAiToolService toolService) {
        this.toolService = toolService;
    }

    public Optional<ShiftAiSummaryDto> getCurrentShiftSummary(AiSecurityContext context, UUID conversationId, long branchId) {
        return toolService.getCurrentShiftSummary(context, conversationId, branchId);
    }

    public Optional<ShiftAiSummaryDto> getOpenShiftStatus(AiSecurityContext context, UUID conversationId, long branchId) {
        return toolService.getOpenShiftStatus(context, conversationId, branchId);
    }

    public List<PaymentBreakdownDto> getPaymentBreakdown(AiSecurityContext context,
                                                         UUID conversationId,
                                                         long branchId,
                                                         AiToolDateRange range) {
        return toolService.getPaymentBreakdown(context, conversationId, branchId, range);
    }
}
