package com.example.valueinsoftbackend.customerbehavior.dto;

import java.util.List;

public record CustomerPreferenceSummary(
        List<CustomerPreferenceItem> topProducts,
        List<CustomerPreferenceItem> topCategories,
        List<String> dataQualityWarnings
) {
}
