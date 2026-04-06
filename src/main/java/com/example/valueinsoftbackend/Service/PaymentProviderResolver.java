package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentProviderResolver {

    private final BillingProperties billingProperties;
    private final Map<String, PaymentProvider> providersByCode;

    public PaymentProviderResolver(BillingProperties billingProperties, List<PaymentProvider> providers) {
        this.billingProperties = billingProperties;
        this.providersByCode = providers.stream()
                .collect(Collectors.toMap(
                        provider -> provider.getProviderCode().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
    }

    public PaymentProvider getActiveProvider() {
        String providerCode = billingProperties.getPaymentProvider() == null
                ? "paymob"
                : billingProperties.getPaymentProvider().trim().toLowerCase(Locale.ROOT);
        PaymentProvider provider = providersByCode.get(providerCode);
        if (provider == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYMENT_PROVIDER_NOT_SUPPORTED",
                    "Unsupported billing payment provider: " + billingProperties.getPaymentProvider()
            );
        }
        return provider;
    }
}
