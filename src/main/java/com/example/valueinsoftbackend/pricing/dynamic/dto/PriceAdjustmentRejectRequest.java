package com.example.valueinsoftbackend.pricing.dynamic.dto;

import jakarta.validation.constraints.NotBlank;

public record PriceAdjustmentRejectRequest(
        @NotBlank String reason
) {
}
