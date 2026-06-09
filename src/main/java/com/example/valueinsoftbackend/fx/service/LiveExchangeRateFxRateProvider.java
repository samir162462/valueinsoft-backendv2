package com.example.valueinsoftbackend.fx.service;

import com.example.valueinsoftbackend.Config.FxDeepSeekProperties;
import com.example.valueinsoftbackend.fx.model.FxDeepSeekFetchResult;
import com.example.valueinsoftbackend.fx.model.FxRatePayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@Slf4j
public class LiveExchangeRateFxRateProvider {

    private final ObjectMapper objectMapper;
    private final FxDeepSeekProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public LiveExchangeRateFxRateProvider(ObjectMapper objectMapper,
                                          FxDeepSeekProperties properties) {
        this(objectMapper, properties, createRestTemplate(properties));
    }

    LiveExchangeRateFxRateProvider(ObjectMapper objectMapper,
                                   FxDeepSeekProperties properties,
                                   RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public FxDeepSeekFetchResult fetchRate() {
        OffsetDateTime requestTimestamp = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(endpointUrl(), String.class);
            OffsetDateTime responseTimestamp = OffsetDateTime.now(ZoneOffset.UTC);
            String rawResponse = response.getBody();
            return new FxDeepSeekFetchResult(
                    parsePayload(rawResponse),
                    rawResponse,
                    requestTimestamp,
                    responseTimestamp
            );
        } catch (HttpStatusCodeException exception) {
            boolean transientFailure = exception.getStatusCode().is5xxServerError()
                    || exception.getStatusCode().value() == 429;
            throw new FxDeepSeekRateException("Live FX provider request failed with HTTP " + exception.getStatusCode().value() + ".",
                    transientFailure,
                    exception.getResponseBodyAsString(),
                    exception);
        } catch (ResourceAccessException exception) {
            throw new FxDeepSeekRateException("Live FX provider is temporarily unavailable.", true, null, exception);
        } catch (FxDeepSeekRateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FxDeepSeekRateException("Live FX provider response could not be parsed.", false, null, exception);
        }
    }

    FxRatePayload parsePayload(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new FxDeepSeekRateException("Live FX provider returned an empty response.", false, rawResponse, null);
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String result = root.path("result").asText("");
            if (!result.isBlank() && !"success".equalsIgnoreCase(result)) {
                throw new FxDeepSeekRateException("Live FX provider did not return success.", false, rawResponse, null);
            }

            String baseCurrency = currency(root.path("base_code").asText(properties.getBaseCurrency()));
            String targetCurrency = currency(properties.getTargetCurrency());
            JsonNode rateNode = root.path("rates").path(targetCurrency);
            if (!rateNode.isNumber()) {
                throw new FxDeepSeekRateException("Live FX provider response is missing rate for " + targetCurrency + ".", false, rawResponse, null);
            }

            OffsetDateTime providerUpdate = providerUpdateTime(root);
            LocalDate effectiveDate = providerUpdate.atZoneSameInstant(rateZoneId()).toLocalDate();
            String provider = root.path("provider").asText("ExchangeRate API");

            return new FxRatePayload(
                    baseCurrency,
                    targetCurrency,
                    rateNode.decimalValue(),
                    "MARKET",
                    effectiveDate,
                    provider + " latest " + baseCurrency + "/" + targetCurrency + " rate at " + providerUpdate,
                    BigDecimal.ONE
            );
        } catch (FxDeepSeekRateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FxDeepSeekRateException("Live FX provider response is not valid JSON.", false, rawResponse, exception);
        }
    }

    private OffsetDateTime providerUpdateTime(JsonNode root) {
        long unix = root.path("time_last_update_unix").asLong(0L);
        if (unix > 0L) {
            return OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(unix), ZoneOffset.UTC);
        }
        String updateUtc = root.path("time_last_update_utc").asText("");
        if (!updateUtc.isBlank()) {
            return ZonedDateTime.parse(updateUtc, DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime();
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String endpointUrl() {
        String template = properties.getEndpointUrl();
        String url = template == null || template.isBlank()
                ? "https://open.er-api.com/v6/latest/{baseCurrency}"
                : template.trim();
        String baseCurrency = currency(properties.getBaseCurrency());
        String targetCurrency = currency(properties.getTargetCurrency());
        return url
                .replace("{baseCurrency}", encode(baseCurrency))
                .replace("{targetCurrency}", encode(targetCurrency));
    }

    private ZoneId rateZoneId() {
        try {
            String configuredZone = properties.getSchedule() == null ? null : properties.getSchedule().getTimeZone();
            return ZoneId.of(configuredZone == null || configuredZone.isBlank() ? "Africa/Cairo" : configuredZone.trim());
        } catch (Exception ignored) {
            return ZoneId.of("Africa/Cairo");
        }
    }

    private String currency(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static RestTemplate createRestTemplate(FxDeepSeekProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = properties == null ? 20_000 : Math.max(1, properties.getTimeoutMs());
        requestFactory.setConnectTimeout(Duration.ofMillis(timeout));
        requestFactory.setReadTimeout(Duration.ofMillis(timeout));
        return new RestTemplate(requestFactory);
    }
}
