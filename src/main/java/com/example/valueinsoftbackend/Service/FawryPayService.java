package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.PaymentTokenRequest;
import com.example.valueinsoftbackend.OnlinePayment.FawryPayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FawryPayService {

    private static final String ITEM_QUANTITY = "1";
    private static final String ITEM_DESCRIPTION = "Valueinsoft billing invoice";
    private static final List<String> CHECKOUT_URL_FIELDS = List.of(
            "redirectUrl",
            "paymentUrl",
            "paymentURL",
            "paymentLink",
            "checkoutUrl",
            "url"
    );

    private final FawryPayProperties fawryPayProperties;
    private final ObjectMapper objectMapper;
    private final RestOperations restOperations;

    @Autowired
    public FawryPayService(FawryPayProperties fawryPayProperties,
                           ObjectMapper objectMapper) {
        this(fawryPayProperties, objectMapper, new RestTemplate());
    }

    FawryPayService(FawryPayProperties fawryPayProperties,
                    ObjectMapper objectMapper,
                    RestOperations restOperations) {
        this.fawryPayProperties = fawryPayProperties;
        this.objectMapper = objectMapper;
        this.restOperations = restOperations;
    }

    public int createFawryPayOrder(int merchantOrderId) {
        validateApiConfiguration();
        return merchantOrderId;
    }

    public String createCheckoutUrl(PaymentTokenRequest request) {
        validateApiConfiguration();
        validateCurrency(request.getCurrency());

        Map<String, Object> payload = buildChargeRequest(request);
        String url = buildFawryPayUrl(fawryPayProperties.getChargePath());
        log.info(
                "Requesting FawryPay checkout URL for merchantRefNum={} branchId={} companyId={} amountCents={} currency={}",
                request.getOrderId(),
                request.getBranchId(),
                request.getCompanyId(),
                request.getAmountCents(),
                request.getCurrency()
        );
        ResponseEntity<String> response = executeJsonPost(url, payload);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return extractCheckoutUrl(response.getBody());
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, "FAWRYPAY_CHECKOUT_FAILED", "FawryPay checkout request failed");
    }

    Map<String, Object> buildChargeRequest(PaymentTokenRequest request) {
        BigDecimal amount = toDecimalAmount(request.getAmountCents());
        String merchantRefNum = String.valueOf(request.getOrderId());
        String customerProfileId = String.valueOf(request.getCompanyId());
        String itemId = "billing-invoice-" + merchantRefNum;
        String returnUrl = fawryPayProperties.getReturnUrl().trim();

        Map<String, Object> chargeItem = new LinkedHashMap<>();
        chargeItem.put("itemId", itemId);
        chargeItem.put("description", ITEM_DESCRIPTION);
        chargeItem.put("price", amount);
        chargeItem.put("quantity", Integer.parseInt(ITEM_QUANTITY));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantCode", fawryPayProperties.getMerchantCode().trim());
        payload.put("merchantRefNum", merchantRefNum);
        payload.put("customerMobile", customerMobile());
        payload.put("customerEmail", customerEmail(request.getCompanyId(), request.getBranchId()));
        payload.put("customerName", "Valueinsoft Branch " + request.getBranchId());
        payload.put("customerProfileId", customerProfileId);
        payload.put("paymentExpiry", String.valueOf(paymentExpiryMillis()));
        payload.put("language", normalizeLanguage(fawryPayProperties.getLanguage()));
        payload.put("chargeItems", List.of(chargeItem));
        payload.put("returnUrl", returnUrl);
        if (fawryPayProperties.getWebhookUrl() != null && !fawryPayProperties.getWebhookUrl().isBlank()) {
            payload.put("orderWebHookUrl", fawryPayProperties.getWebhookUrl().trim());
        }
        if (fawryPayProperties.getPaymentMethod() != null && !fawryPayProperties.getPaymentMethod().isBlank()) {
            payload.put("paymentMethod", fawryPayProperties.getPaymentMethod().trim());
        }
        payload.put("authCaptureModePayment", fawryPayProperties.isAuthCaptureModePayment());
        payload.put("signature", buildChargeSignature(merchantRefNum, customerProfileId, returnUrl, List.of(chargeItem)));
        return payload;
    }

    String buildChargeSignature(String merchantRefNum,
                                String customerProfileId,
                                String returnUrl,
                                List<Map<String, Object>> chargeItems) {
        List<Map<String, Object>> sortedItems = new ArrayList<>(chargeItems);
        sortedItems.sort((left, right) -> String.valueOf(left.get("itemId")).compareTo(String.valueOf(right.get("itemId"))));

        StringBuilder builder = new StringBuilder();
        builder.append(fawryPayProperties.getMerchantCode().trim());
        builder.append(merchantRefNum);
        builder.append(customerProfileId == null ? "" : customerProfileId);
        builder.append(returnUrl);
        for (Map<String, Object> chargeItem : sortedItems) {
            builder.append(chargeItem.get("itemId"));
            builder.append(chargeItem.get("quantity"));
            builder.append(formatPrice(chargeItem.get("price")));
        }
        builder.append(fawryPayProperties.getSecureHashKey().trim());
        return sha256(builder.toString());
    }

    private void validateApiConfiguration() {
        if (fawryPayProperties.getBaseUrl() == null || fawryPayProperties.getBaseUrl().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_NOT_CONFIGURED", "FawryPay base URL is not configured");
        }
        if (fawryPayProperties.getChargePath() == null || fawryPayProperties.getChargePath().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_NOT_CONFIGURED", "FawryPay charge path is not configured");
        }
        if (fawryPayProperties.getMerchantCode() == null || fawryPayProperties.getMerchantCode().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_NOT_CONFIGURED", "FawryPay merchant code is not configured");
        }
        if (fawryPayProperties.getSecureHashKey() == null || fawryPayProperties.getSecureHashKey().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_NOT_CONFIGURED", "FawryPay secure hash key is not configured");
        }
        if (fawryPayProperties.getReturnUrl() == null || fawryPayProperties.getReturnUrl().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_NOT_CONFIGURED", "FawryPay return URL is not configured");
        }
    }

    private void validateCurrency(String currency) {
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.isBlank() && !"EGP".equals(normalizedCurrency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FAWRYPAY_CURRENCY_NOT_SUPPORTED", "FawryPay checkout currently supports EGP only");
        }
    }

    private String extractCheckoutUrl(String responseBody) {
        String trimmedBody = responseBody == null ? "" : responseBody.trim();
        if (trimmedBody.startsWith("http://") || trimmedBody.startsWith("https://")) {
            return trimmedBody;
        }

        try {
            JsonNode root = objectMapper.readTree(trimmedBody);
            String url = extractCheckoutUrl(root);
            if (url != null && !url.isBlank()) {
                return url;
            }
            String statusDescription = root.path("statusDescription").asText("");
            String statusCode = root.path("statusCode").asText("");
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "FAWRYPAY_CHECKOUT_URL_MISSING",
                    "FawryPay response did not include a checkout URL",
                    List.of("statusCode=" + statusCode, "statusDescription=" + statusDescription)
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FAWRYPAY_INVALID_RESPONSE", "Invalid FawryPay checkout response");
        }
    }

    private String extractCheckoutUrl(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            return value.startsWith("http://") || value.startsWith("https://") ? value : null;
        }
        for (String fieldName : CHECKOUT_URL_FIELDS) {
            JsonNode field = node.get(fieldName);
            if (field != null && field.isTextual() && !field.asText().isBlank()) {
                return field.asText();
            }
        }
        JsonNode dataNode = node.get("data");
        if (dataNode != null) {
            return extractCheckoutUrl(dataNode);
        }
        return null;
    }

    private ResponseEntity<String> executeJsonPost(String url, Map<String, Object> payload) {
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, buildJsonHeaders());
            ResponseEntity<String> response = restOperations.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("FawryPay request failed with status={} bodyPresent={}", response.getStatusCode(), response.getBody() != null);
            }
            return response;
        } catch (HttpStatusCodeException exception) {
            log.error(
                    "FawryPay HTTP request failed for url={} status={} body={}",
                    url,
                    exception.getStatusCode().value(),
                    sanitizeFawryPayErrorBody(exception.getResponseBodyAsString())
            );
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "FAWRYPAY_CHECKOUT_FAILED",
                    "FawryPay checkout request failed",
                    List.of("FawryPay status " + exception.getStatusCode().value() + ": " + sanitizeFawryPayErrorBody(exception.getResponseBodyAsString()))
            );
        } catch (RestClientException exception) {
            log.error("FawryPay HTTP request failed for url={}", url, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FAWRYPAY_CHECKOUT_FAILED", "FawryPay checkout request failed");
        }
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String buildFawryPayUrl(String path) {
        String baseUrl = fawryPayProperties.getBaseUrl().trim();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBaseUrl + normalizedPath;
    }

    private BigDecimal toDecimalAmount(long amountCents) {
        return BigDecimal.valueOf(amountCents, 2).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatPrice(Object value) {
        if (value instanceof BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private long paymentExpiryMillis() {
        int minutes = Math.max(1, fawryPayProperties.getPaymentExpiryMinutes());
        return Instant.now().plus(minutes, ChronoUnit.MINUTES).toEpochMilli();
    }

    private String customerMobile() {
        return fawryPayProperties.getDefaultCustomerMobile() == null || fawryPayProperties.getDefaultCustomerMobile().isBlank()
                ? "01000000000"
                : fawryPayProperties.getDefaultCustomerMobile().trim();
    }

    private String customerEmail(int companyId, int branchId) {
        return "billing+" + companyId + "-" + branchId + "@valueinsoft.com";
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null || language.isBlank() ? "en-gb" : language.trim().toLowerCase(Locale.ROOT);
        return "ar-eg".equals(normalized) ? "ar-eg" : "en-gb";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(encoded.length * 2);
            for (byte item : encoded) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FAWRYPAY_SIGNATURE_FAILED", "Failed to compute FawryPay request signature");
        }
    }

    private String sanitizeFawryPayErrorBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty response";
        }

        return responseBody
                .replaceAll("(?i)\"signature\"\\s*:\\s*\"[^\"]+\"", "\"signature\":\"***\"")
                .replaceAll("(?i)\"merchantCode\"\\s*:\\s*\"[^\"]+\"", "\"merchantCode\":\"***\"");
    }
}
