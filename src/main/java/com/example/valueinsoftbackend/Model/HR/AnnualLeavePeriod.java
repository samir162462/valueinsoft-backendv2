package com.example.valueinsoftbackend.Model.HR;

import java.time.LocalDate;

public record AnnualLeavePeriod(
        int userId,
        int branchId,
        LocalDate startDate,
        LocalDate endDate
) {
}
