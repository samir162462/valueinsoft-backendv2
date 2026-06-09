package com.example.valueinsoftbackend.fx.model;

import java.math.BigDecimal;

public record FxCompanyConfig(
        int companyId,
        BigDecimal safetyBufferPercentage,
        String selectedRateType,
        int configVersion
) {
}
