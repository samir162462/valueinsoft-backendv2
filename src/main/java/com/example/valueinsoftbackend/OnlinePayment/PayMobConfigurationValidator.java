package com.example.valueinsoftbackend.OnlinePayment;

import com.example.valueinsoftbackend.Config.BillingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class PayMobConfigurationValidator {

    private final BillingProperties billingProperties;
    private final PayMobProperties payMobProperties;

    public PayMobConfigurationValidator(BillingProperties billingProperties,
                                        PayMobProperties payMobProperties) {
        this.billingProperties = billingProperties;
        this.payMobProperties = payMobProperties;
    }

    @PostConstruct
    void validate() {
        String providerCode = billingProperties.getPaymentProvider() == null
                ? ""
                : billingProperties.getPaymentProvider().trim().toLowerCase(Locale.ROOT);

        if (!"paymob".equals(providerCode)) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (payMobProperties.getAuthToken() == null || payMobProperties.getAuthToken().isBlank()) {
            missing.add("VLS_PAYMOB_AUTH_TOKEN");
        }
        if (payMobProperties.getCardIntegrationId() <= 0) {
            missing.add("VLS_PAYMOB_CARD_INTEGRATION_ID");
        }
        if (payMobProperties.getCardIFrameId() <= 0) {
            missing.add("VLS_PAYMOB_CARD_IFRAME_ID");
        }
        if (payMobProperties.getHmacSecret() == null || payMobProperties.getHmacSecret().isBlank()) {
            missing.add("VLS_PAYMOB_HMAC_SECRET");
        }
        if (!missing.isEmpty()) {
            log.warn("PayMob is the active billing provider but required configuration is missing: {}", String.join(", ", missing));
        }
    }
}
