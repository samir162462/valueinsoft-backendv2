package com.example.valueinsoftbackend.customerbehavior.repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

public record CustomerBehaviorQueryScope(
        long companyId,
        List<Integer> branchIds,
        LocalDate fromDate,
        LocalDate toDate,
        Timestamp fromTime,
        Timestamp toTimeExclusive
) {
}
