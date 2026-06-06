package com.example.valueinsoftbackend.customerbehavior.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class CustomerBehaviorMath {

    private static final int SCALE = 4;

    private CustomerBehaviorMath() {
    }

    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal safeDenominator = zeroIfNull(denominator);
        if (safeDenominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return zeroIfNull(numerator).divide(safeDenominator, SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal divide(BigDecimal numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return zeroIfNull(numerator).divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }
}
