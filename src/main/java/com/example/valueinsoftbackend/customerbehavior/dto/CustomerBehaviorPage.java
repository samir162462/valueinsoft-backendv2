package com.example.valueinsoftbackend.customerbehavior.dto;

import java.util.List;

public record CustomerBehaviorPage<T>(
        List<T> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {
}
