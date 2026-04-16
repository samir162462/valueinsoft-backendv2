package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Min;

public record CloseShiftRequest(
        @Min(value = 0, message = "countedCash must be zero or greater")
        double countedCash,

        String varianceReason,
        String closeNote
) {}
