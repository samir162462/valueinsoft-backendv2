package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerBehaviorOverview(
        LocalDate fromDate,
        LocalDate toDate,
        List<Integer> branchIds,
        String currencyCode,
        String timezone,
        long totalRegisteredCustomers,
        long purchasingCustomers,
        long activeCustomers,
        long repeatCustomers,
        BigDecimal repeatPurchaseRate,
        BigDecimal averageOrderValue,
        BigDecimal netCustomerSpend,
        BigDecimal grossCustomerSpend,
        BigDecimal discountTotal,
        BigDecimal returnTotal,
        BigDecimal averageBasketSize,
        long atRiskCustomerCount,
        List<CustomerSegmentSummary> segments,
        List<String> dataQualityWarnings
) {
}
