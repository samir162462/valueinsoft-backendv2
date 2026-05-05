package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.math.BigDecimal;

public record OfflineBootstrapDiscountItem(
        String discountCode,
        String name,
        String type,
        BigDecimal value,
        Boolean active
) {
}
