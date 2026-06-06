package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerBehaviorRow(
        long customerId,
        String customerName,
        String maskedPhone,
        Integer branchId,
        String branchName,
        CustomerSegment segment,
        List<CustomerSegment> secondaryFlags,
        LocalDateTime registeredAt,
        LocalDateTime lastPurchaseAt,
        Long daysSinceLastPurchase,
        long orders,
        long historicalOrders,
        BigDecimal totalSpend,
        BigDecimal grossSpend,
        BigDecimal discountTotal,
        BigDecimal returnTotal,
        BigDecimal averageOrderValue,
        BigDecimal monetary,
        BigDecimal averagePurchaseCadenceDays,
        BigDecimal averageBasketSize,
        String favoriteProduct,
        String favoriteCategory,
        BigDecimal discountRatio,
        BigDecimal returnRatio
) {
}
