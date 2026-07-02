package com.example.valueinsoftbackend.Service.payment;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentProviderResolver {

    private final BillingProperties billingProperties;
    private final Map<String, PaymentProvider> providersByCode;

    public PaymentProviderResolver(BillingProperties billingProperties, List<PaymentProvider> providers) {
        this.billingProperties = billingProperties;
        this.providersByCode = providers.stream()
                .flatMap(provider -> {
                    List<String> aliases = provider.getProviderAliases();
                    return java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(provider.getProviderCode()),
                            aliases == null ? java.util.stream.Stream.empty() : aliases.stream()
                    ).map(code -> Map.entry(code.toLowerCase(Locale.ROOT), provider));
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing
                ));
    }

    public PaymentProvider getActiveProvider() {
        String providerCode = billingProperties.getPaymentProvider() == null
                ? "paymob"
                : billingProperties.getPaymentProvider().trim().toLowerCase(Locale.ROOT);
        return getProvider(providerCode);
    }

    public PaymentProvider getProvider(String providerCode) {
        String normalizedProviderCode = providerCode == null || providerCode.isBlank()
                ? "paymob"
                : providerCode.trim().toLowerCase(Locale.ROOT);
        PaymentProvider provider = providersByCode.get(normalizedProviderCode);
        if (provider == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYMENT_PROVIDER_NOT_SUPPORTED",
                    "Unsupported billing payment provider: " + providerCode
            );
        }
        return provider;
    }
}
