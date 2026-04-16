package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CashMovementRequest(
        @NotBlank(message = "movementType is required")
        String movementType,

        @Positive(message = "amount must be positive")
        double amount,

        String note,
        Integer clientId,
        String associatedUserId,
        String referenceType,
        String referenceId
) {}
