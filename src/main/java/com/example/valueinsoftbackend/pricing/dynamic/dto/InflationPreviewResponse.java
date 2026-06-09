package com.example.valueinsoftbackend.pricing.dynamic.dto;

import java.util.List;

public record InflationPreviewResponse(
        int totalProducts,
        List<InflationPreviewItem> items
) {}
