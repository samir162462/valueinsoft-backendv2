package com.example.valueinsoftbackend.Model.HR;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AnnualLeaveRequestCreateRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 2000) String notes
) {
}
