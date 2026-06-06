package com.example.valueinsoftbackend.customerbehavior.dto;

import java.util.List;

public record CustomerBehaviorProfile(
        CustomerBehaviorRow summary,
        List<CustomerRecentOrder> recentOrders,
        List<CustomerPreferenceItem> favoriteProducts,
        List<CustomerPreferenceItem> favoriteCategories,
        List<CustomerSpendTrendPoint> spendTrend,
        String aiRecommendation,
        List<String> dataQualityWarnings
) {
}
