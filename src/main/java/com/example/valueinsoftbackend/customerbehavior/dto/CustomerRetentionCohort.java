package com.example.valueinsoftbackend.customerbehavior.dto;

import java.math.BigDecimal;

public record CustomerRetentionCohort(
        String cohortMonth,
        long customers,
        long repeatCustomers,
        BigDecimal repeatRate,
        BigDecimal netSpend
) {
}
