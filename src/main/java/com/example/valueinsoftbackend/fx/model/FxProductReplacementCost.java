package com.example.valueinsoftbackend.fx.model;

import java.math.BigDecimal;

public record FxProductReplacementCost(
        long productId,
        BigDecimal replacementCostUsd
) {
}
