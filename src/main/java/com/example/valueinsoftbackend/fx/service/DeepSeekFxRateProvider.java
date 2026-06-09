package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.ai.provider.AiProviderException;
import com.example.valueinsoftbackend.ai.provider.DeepSeekAiProvider;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.example.valueinsoftbackend.fx.model.FxDeepSeekFetchResult;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class DeepSeekFxRateProvider {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "baseCurrency",
            "targetCurrency",
            "rate",
            "rateType",
            "effectiveDate",
            "sourceDescription",
            "confidence"
    );

    private final DeepSeekAiProvider deepSeekAiProvider;
    private final ObjectMapper objectMapper;
    private final FxDeepSeekProperties properties;

    public DeepSeekFxRateProvider(DeepSeekAiProvider deepSeekAiProvider,
                                  ObjectMapper objectMapper,
                                  FxDeepSeekProperties properties) {
        this.deepSeekAiProvider = deepSeekAiProvider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public FxDeepSeekFetchResult fetchRate() {
        int maxAttempts = Math.max(1, properties.getRetry().getMaxAttempts());
        long delayMillis = Math.max(0L, properties.getRetry().getInitialDelaySeconds()) * 1_000L;
        BigDecimal multiplier = properties.getRetry().getMultiplier() == null
                ? BigDecimal.ONE
                : properties.getRetry().getMultiplier();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            OffsetDateTime requestTimestamp = OffsetDateTime.now(ZoneOffset.UTC);
            try {
                AiModelResponse response = deepSeekAiProvider.generate(buildRequest());
                OffsetDateTime responseTimestamp = OffsetDateTime.now(ZoneOffset.UTC);
                String rawResponse = response.answer();
                return new FxDeepSeekFetchResult(
                        parsePayload(rawResponse),
                        rawResponse,
                        requestTimestamp,
                        responseTimestamp
                );
            } catch (AiProviderException exception) {
                boolean transientFailure = isTransient(exception);
                if (!transientFailure || attempt >= maxAttempts) {
                    throw new FxDeepSeekRateException(exception.getSafeMessage(), transientFailure, null, exception);
                }
                log.warn("DeepSeek FX rate attempt {}/{} failed transiently: {}", attempt, maxAttempts, exception.getSafeMessage());
                sleep(delayMillis);
                delayMillis = nextDelay(delayMillis, multiplier);
            } catch (FxDeepSeekRateException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new FxDeepSeekRateException("DeepSeek FX response could not be parsed.", false, null, exception);
            }
        }

        throw new FxDeepSeekRateException("DeepSeek FX rate retrieval did not complete.", true);
    }

    FxRatePayload parsePayload(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new FxDeepSeekRateException("DeepSeek returned an empty FX response.", false, rawResponse, null);
        }

        String json = extractJsonOnly(rawResponse);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new FxDeepSeekRateException("DeepSeek FX response must be a JSON object.", false, rawResponse, null);
            }
            rejectUnknownFields(root, rawResponse);
            return new FxRatePayload(
                    text(root, "baseCurrency"),
                    text(root, "targetCurrency"),
                    decimal(root, "rate"),
                    text(root, "rateType"),
                    localDate(root, "effectiveDate"),
                    text(root, "sourceDescription"),
                    optionalDecimal(root, "confidence")
            );
        } catch (FxDeepSeekRateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FxDeepSeekRateException("DeepSeek FX response is not valid structured JSON.", false, rawResponse, exception);
        }
    }

    private AiModelRequest buildRequest() {
        String baseCurrency = currency(properties.getBaseCurrency());
        String targetCurrency = currency(properties.getTargetCurrency());
        LocalDate today = LocalDate.now(rateZoneId());
        String userPrompt = """
                Determine the real-time current %s-to-%s exchange rate for today's FX validation date.

                Backend validation date: %s.
                The effectiveDate must equal %s exactly.
                Return today's current reference rate only. Do not return stale, historical, weekend, previous-business-day, cutoff, or forecast rates.

                Return JSON only using this exact structure:
                {
                  "baseCurrency": "%s",
                  "targetCurrency": "%s",
                  "rate": 0.000000,
                  "rateType": "REFERENCE",
                  "effectiveDate": "YYYY-MM-DD",
                  "sourceDescription": "string",
                  "confidence": 0.00
                }

                Do not include markdown, commentary, or additional fields.
                """.formatted(
                baseCurrency,
                targetCurrency,
                today,
                today,
                baseCurrency,
                targetCurrency
        );
        return new AiModelRequest(
                "You are a backend data extraction service. Return structured JSON only.",
                userPrompt,
                "FX_RATE_REFERENCE",
                "",
                "",
                "deepseek"
        );
    }

    private String extractJsonOnly(String rawResponse) {
        String trimmed = rawResponse.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline < 0) {
                throw new FxDeepSeekRateException("DeepSeek FX response code fence did not contain JSON.", false, rawResponse, null);
            }
            trimmed = trimmed.substring(firstNewline + 1).trim();
            if (!trimmed.endsWith("```")) {
                throw new FxDeepSeekRateException("DeepSeek FX response contained unterminated markdown.", false, rawResponse, null);
            }
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new FxDeepSeekRateException("DeepSeek FX response contained unrelated text.", false, rawResponse, null);
        }
        return trimmed;
    }

    private void rejectUnknownFields(JsonNode root, String rawResponse) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_FIELDS.contains(name)) {
                throw new FxDeepSeekRateException("DeepSeek FX response contained unexpected field: " + name, false, rawResponse, null);
            }
        }
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        return node == null || node.isNull() ? null : node.asText();
    }

    private BigDecimal decimal(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    private BigDecimal optionalDecimal(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new FxDeepSeekRateException("DeepSeek FX confidence must be numeric when provided.", false, root.toString(), null);
        }
        return node.decimalValue();
    }

    private LocalDate localDate(JsonNode root, String fieldName) {
        String value = text(root, fieldName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private boolean isTransient(AiProviderException exception) {
        return exception.getCategory() == AiProviderException.Category.PROVIDER_TIMEOUT
                || exception.getCategory() == AiProviderException.Category.PROVIDER_RATE_LIMIT
                || exception.getCategory() == AiProviderException.Category.PROVIDER_SERVER_ERROR;
    }

    private long nextDelay(long currentDelayMillis, BigDecimal multiplier) {
        if (currentDelayMillis <= 0) {
            return 0L;
        }
        BigDecimal safeMultiplier = multiplier.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : multiplier;
        return BigDecimal.valueOf(currentDelayMillis).multiply(safeMultiplier).longValue();
    }

    private void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofMillis(delayMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FxDeepSeekRateException("DeepSeek FX retry was interrupted.", true, null, exception);
        }
    }

    private String currency(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ZoneId rateZoneId() {
        try {
            String configuredZone = properties.getSchedule() == null ? null : properties.getSchedule().getTimeZone();
            return ZoneId.of(configuredZone == null || configuredZone.isBlank() ? "Africa/Cairo" : configuredZone.trim());
        } catch (Exception ignored) {
            return ZoneId.of("Africa/Cairo");
        }
    }
}
