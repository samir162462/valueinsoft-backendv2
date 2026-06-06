package com.example.valueinsoftbackend.customerbehavior.dto;

import java.time.LocalDate;
import java.util.List;

public record CustomerBehaviorFilter(
        List<Integer> branchIds,
        LocalDate fromDate,
        LocalDate toDate,
        CustomerSegment segment,
        Integer minOrders,
        String search,
        Integer page,
        Integer pageSize,
        String sortBy,
        String sortDirection
) {
}
