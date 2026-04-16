package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OpenShiftRequest(
        @Positive(message = "branchId must be positive")
        int branchId,

        @PositiveOrZero(message = "openingFloat must be zero or greater")
        double openingFloat,

        String registerCode
) {}
