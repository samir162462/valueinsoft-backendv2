package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderPeriodRequest(
        @Positive(message = "branchId must be greater than zero")
        int branchId,

        @NotBlank(message = "startTime is required")
        String startTime,

        @NotBlank(message = "endTime is required")
        String endTime
) {}
