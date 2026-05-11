package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
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
}
