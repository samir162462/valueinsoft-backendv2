package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.example.valueinsoftbackend.fx.model.FxValidationResult;
import com.example.valueinsoftbackend.fx.model.GlobalFxRateSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@Service
public class FxRateValidator {

    private final FxDeepSeekProperties properties;
    private final FxWeekPeriodService weekPeriodService;

    public FxRateValidator(FxDeepSeekProperties properties,
                           FxWeekPeriodService weekPeriodService) {
        this.properties = properties;
        this.weekPeriodService = weekPeriodService;
    }

    public FxValidationResult validate(FxRatePayload payload, Optional<GlobalFxRateSnapshot> previousValidRate) {
        if (payload == null) {
            return FxValidationResult.rejected("DeepSeek FX response is missing.");
        }
        if (!currency(properties.getBaseCurrency()).equals(currency(payload.baseCurrency()))) {
            return FxValidationResult.rejected("baseCurrency must equal " + currency(properties.getBaseCurrency()) + ".");
        }
        if (!currency(properties.getTargetCurrency()).equals(currency(payload.targetCurrency()))) {
            return FxValidationResult.rejected("targetCurrency must equal " + currency(properties.getTargetCurrency()) + ".");
        }
        if (payload.rate() == null) {
            return FxValidationResult.rejected("rate is required.");
        }
        if (payload.rate().compareTo(BigDecimal.ZERO) <= 0) {
            return FxValidationResult.rejected("rate must be greater than zero.");
        }
        if (payload.effectiveDate() == null) {
            return FxValidationResult.rejected("effectiveDate is required.");
        }

        LocalDate today = LocalDate.now(weekPeriodService.zoneId());
        if (!payload.effectiveDate().isEqual(today)) {
            return FxValidationResult.rejected("effectiveDate must equal today's FX validation date: " + today + ".");
        }
        if (properties.getMinimumRate() != null && payload.rate().compareTo(properties.getMinimumRate()) < 0) {
            return FxValidationResult.rejected("rate is below configured minimum-rate.");
        }
        if (properties.getMaximumRate() != null && payload.rate().compareTo(properties.getMaximumRate()) > 0) {
            return FxValidationResult.rejected("rate is above configured maximum-rate.");
        }
        if (payload.confidence() != null
                && properties.getMinimumConfidence() != null
                && payload.confidence().compareTo(properties.getMinimumConfidence()) < 0) {
            return FxValidationResult.rejected("confidence is below configured minimum-confidence.");
        }
        if (previousValidRate.isPresent()) {
            FxValidationResult movement = validateMovement(payload.rate(), previousValidRate.get().rate());
            if (!movement.valid()) {
                return movement;
            }
        }
        return FxValidationResult.accepted();
    }

    private FxValidationResult validateMovement(BigDecimal newRate, BigDecimal previousRate) {
        if (previousRate == null || previousRate.compareTo(BigDecimal.ZERO) <= 0 || properties.getMaximumChangePercentage() == null) {
            return FxValidationResult.accepted();
        }
        BigDecimal changePercentage = newRate.subtract(previousRate)
                .abs()
                .multiply(new BigDecimal("100.0000"))
                .divide(previousRate, 4, RoundingMode.HALF_UP);
        if (changePercentage.compareTo(properties.getMaximumChangePercentage()) > 0) {
            return FxValidationResult.rejected("rate movement exceeds configured maximum-change-percentage.");
        }
        return FxValidationResult.accepted();
    }

    private String currency(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
