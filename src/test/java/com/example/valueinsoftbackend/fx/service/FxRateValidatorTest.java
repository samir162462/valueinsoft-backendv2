package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.example.valueinsoftbackend.fx.model.FxRefreshTrigger;
import com.example.valueinsoftbackend.fx.model.FxValidationResult;
import com.example.valueinsoftbackend.fx.model.GlobalFxRateSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxRateValidatorTest {

    private final FxDeepSeekProperties properties = properties();
    private final FxRateValidator validator = new FxRateValidator(properties, new FxWeekPeriodService(properties));

    @Test
    void acceptsValidUsdEgpRateInsideConfiguredBounds() {
        FxValidationResult result = validator.validate(payload("USD", "EGP", "50.00000000"), Optional.empty());

        assertTrue(result.valid());
    }

    @Test
    void rejectsWrongCurrencyPair() {
        FxValidationResult result = validator.validate(payload("EUR", "EGP", "50.00000000"), Optional.empty());

        assertFalse(result.valid());
    }

    @Test
    void rejectsZeroOrNegativeRate() {
        FxValidationResult result = validator.validate(payload("USD", "EGP", "0.00000000"), Optional.empty());

        assertFalse(result.valid());
    }

    @Test
    void rejectsUnexpectedFutureEffectiveDate() {
        FxRatePayload payload = new FxRatePayload(
                "USD",
                "EGP",
                new BigDecimal("50.00000000"),
                "REFERENCE",
                LocalDate.now().plusDays(1),
                "DeepSeek",
                new BigDecimal("0.9000")
        );

        FxValidationResult result = validator.validate(payload, Optional.empty());

        assertFalse(result.valid());
    }

    @Test
    void rejectsOlderEffectiveDateEvenWhenInsidePreviousAgeWindow() {
        FxRatePayload payload = new FxRatePayload(
                "USD",
                "EGP",
                new BigDecimal("50.00000000"),
                "REFERENCE",
                LocalDate.now().minusDays(1),
                "DeepSeek",
                new BigDecimal("0.9000")
        );

        FxValidationResult result = validator.validate(payload, Optional.empty());

        assertFalse(result.valid());
    }

    @Test
    void rejectsExcessiveMovementFromPreviousApprovedRate() {
        FxValidationResult result = validator.validate(
                payload("USD", "EGP", "80.00000000"),
                Optional.of(snapshot("50.00000000"))
        );

        assertFalse(result.valid());
    }

    private FxRatePayload payload(String baseCurrency, String targetCurrency, String rate) {
        return new FxRatePayload(
                baseCurrency,
                targetCurrency,
                new BigDecimal(rate),
                "REFERENCE",
                LocalDate.now(),
                "DeepSeek",
                new BigDecimal("0.9000")
        );
    }

    private GlobalFxRateSnapshot snapshot(String rate) {
        OffsetDateTime now = OffsetDateTime.now();
        return new GlobalFxRateSnapshot(
                1L,
                "USD",
                "EGP",
                LocalDate.now(),
                LocalDate.now(),
                new BigDecimal(rate),
                "REFERENCE",
                "DEEPSEEK",
                "previous",
                new BigDecimal("0.9000"),
                now,
                now,
                "{}",
                "VALID",
                "VALID",
                "VALID",
                false,
                true,
                FxRefreshTrigger.SCHEDULED,
                now,
                now
        );
    }

    private FxDeepSeekProperties properties() {
        FxDeepSeekProperties props = new FxDeepSeekProperties();
        props.setMinimumRate(new BigDecimal("10.00000000"));
        props.setMaximumRate(new BigDecimal("150.00000000"));
        props.setMaximumChangePercentage(new BigDecimal("20.0000"));
        props.setMinimumConfidence(new BigDecimal("0.5000"));
        return props;
    }
}
