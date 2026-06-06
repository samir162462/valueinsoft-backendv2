package com.example.valueinsoftbackend.customerbehavior.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerBehaviorMetricRecord(
        long customerId,
        String customerName,
        String customerPhone,
        Integer branchId,
        String branchName,
        LocalDateTime registeredAt,
        long periodOrderCount,
        long historicalOrderCount,
        BigDecimal grossSpend,
        BigDecimal discountTotal,
        BigDecimal returnTotal,
        BigDecimal netSpend,
        BigDecimal purchasedQuantity,
        LocalDateTime firstPurchaseAt,
        LocalDateTime lastPurchaseAt,
        String favoriteProduct,
        String favoriteCategory,
        BigDecimal favoriteCategorySpend,
        BigDecimal totalCategorySpend
) {
}
