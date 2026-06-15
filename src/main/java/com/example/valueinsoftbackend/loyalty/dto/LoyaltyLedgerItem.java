package com.example.valueinsoftbackend.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyLedgerItem(
        long ledgerId,
        long loyaltyAccountId,
        int clientId,
        int branchId,
        String movementType,
        int pointsDelta,
        BigDecimal monetaryValue,
        String sourceType,
        String sourceId,
        Integer orderId,
        Integer orderDetailId,
        String note,
        String createdBy,
        LocalDateTime createdAt
) {
}
