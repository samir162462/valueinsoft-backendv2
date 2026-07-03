package com.example.valueinsoftbackend.companyinsights.api.dto;

import java.util.List;

public record InsightPageDto(
        List<InsightDto> items,
        int page,
        int pageSize,
        long total,
        int totalPages
) {
}
