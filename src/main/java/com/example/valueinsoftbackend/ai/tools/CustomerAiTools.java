package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorPage;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorProfile;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorRow;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerPreferenceSummary;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerProductAffinity;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRetentionCohort;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSegmentSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerAiTools {

    private final CustomerAiToolService toolService;

    public CustomerAiTools(CustomerAiToolService toolService) {
        this.toolService = toolService;
    }

    public List<CustomerAiDto> searchCustomer(AiSecurityContext context,
                                              UUID conversationId,
                                              long branchId,
                                              String customerNameOrPhone) {
        return toolService.searchCustomer(context, conversationId, branchId, customerNameOrPhone);
    }

    public Optional<CustomerBalanceAiDto> getCustomerBalance(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            long customerId) {
        return toolService.getCustomerBalance(context, conversationId, branchId, customerId);
    }

    public List<CustomerOrderAiDto> getCustomerLastOrders(AiSecurityContext context,
                                                          UUID conversationId,
                                                          long branchId,
                                                          long customerId,
                                                          Integer limit) {
        return toolService.getCustomerLastOrders(context, conversationId, branchId, customerId, limit);
    }

    public List<CustomerSegmentSummary> getCustomerSegments(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            AiToolDateRange range) {
        return toolService.getCustomerSegments(context, conversationId, branchId, range);
    }

    public CustomerBehaviorPage<CustomerBehaviorRow> getAtRiskCustomers(AiSecurityContext context,
                                                                        UUID conversationId,
                                                                        long branchId,
                                                                        AiToolDateRange range,
                                                                        Integer limit) {
        return toolService.getAtRiskCustomers(context, conversationId, branchId, range, limit);
    }

    public CustomerPreferenceSummary getCustomerPreferences(AiSecurityContext context,
                                                            UUID conversationId,
                                                            long branchId,
                                                            AiToolDateRange range,
                                                            Integer limit) {
        return toolService.getCustomerPreferences(context, conversationId, branchId, range, limit);
    }

    public CustomerBehaviorProfile getCustomerPurchasePattern(AiSecurityContext context,
                                                              UUID conversationId,
                                                              long branchId,
                                                              long customerId,
                                                              AiToolDateRange range) {
        return toolService.getCustomerPurchasePattern(context, conversationId, branchId, customerId, range);
    }

    public List<CustomerProductAffinity> getCustomerAffinityProducts(AiSecurityContext context,
                                                                     UUID conversationId,
                                                                     long branchId,
                                                                     AiToolDateRange range) {
        return toolService.getCustomerAffinityProducts(context, conversationId, branchId, range);
    }

    public List<CustomerRetentionCohort> getCustomerRetentionCohorts(AiSecurityContext context,
                                                                     UUID conversationId,
                                                                     long branchId,
                                                                     AiToolDateRange range) {
        return toolService.getCustomerRetentionCohorts(context, conversationId, branchId, range);
    }
}
