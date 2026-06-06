package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerRecentOrder(
        long orderId,
        int branchId,
        LocalDateTime orderTime,
        String orderType,
        BigDecimal grossTotal,
        BigDecimal discountTotal,
        BigDecimal returnTotal,
        BigDecimal netTotal
) {
}
