package com.example.valueinsoftbackend.pos.offline.dto.response;

import java.math.BigDecimal;

public record OfflineBootstrapTaxItem(
        String taxCode,
        String name,
        BigDecimal rate,
        Boolean active
) {
}
