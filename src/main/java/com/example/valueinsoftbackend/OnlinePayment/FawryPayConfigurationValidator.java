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
public class FawryPayConfigurationValidator {

    private final BillingProperties billingProperties;
    private final FawryPayProperties fawryPayProperties;

    public FawryPayConfigurationValidator(BillingProperties billingProperties,
                                          FawryPayProperties fawryPayProperties) {
        this.billingProperties = billingProperties;
        this.fawryPayProperties = fawryPayProperties;
    }

    @PostConstruct
    void validate() {
        String providerCode = billingProperties.getPaymentProvider() == null
                ? ""
                : billingProperties.getPaymentProvider().trim().toLowerCase(Locale.ROOT);

        if (!List.of("fawrypay", "fawry", "fawry_pay").contains(providerCode)) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (fawryPayProperties.getBaseUrl() == null || fawryPayProperties.getBaseUrl().isBlank()) {
            missing.add("VLS_FAWRYPAY_BASE_URL");
        }
        if (fawryPayProperties.getMerchantCode() == null || fawryPayProperties.getMerchantCode().isBlank()) {
            missing.add("VLS_FAWRYPAY_MERCHANT_CODE");
        }
        if (fawryPayProperties.getSecureHashKey() == null || fawryPayProperties.getSecureHashKey().isBlank()) {
            missing.add("VLS_FAWRYPAY_SECURE_HASH_KEY");
        }
        if (fawryPayProperties.getReturnUrl() == null || fawryPayProperties.getReturnUrl().isBlank()) {
            missing.add("VLS_FAWRYPAY_RETURN_URL");
        }
        if (!missing.isEmpty()) {
            log.warn("FawryPay is the active billing provider but required configuration is missing: {}", String.join(", ", missing));
        }
    }
}
