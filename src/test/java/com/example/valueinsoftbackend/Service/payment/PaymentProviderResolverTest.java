package com.example.valueinsoftbackend.Service.payment;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class PaymentProviderResolverTest {

    @Test
    void activeProviderResolvesCanonicalCode() {
        BillingProperties properties = new BillingProperties();
        properties.setPaymentProvider("mock");
        PaymentProvider mockProvider = provider("mock", List.of());
        PaymentProviderResolver resolver = new PaymentProviderResolver(properties, List.of(mockProvider));

        assertSame(mockProvider, resolver.getActiveProvider());
    }

    @Test
    void activeProviderResolvesAliasCode() {
        BillingProperties properties = new BillingProperties();
        properties.setPaymentProvider("fawry");
        PaymentProvider fawryProvider = provider("fawrypay", List.of("fawry", "fawry_pay"));
        PaymentProviderResolver resolver = new PaymentProviderResolver(properties, List.of(fawryProvider));

        assertSame(fawryProvider, resolver.getActiveProvider());
    }

    @Test
    void unsupportedProviderRaisesConfigurationError() {
        BillingProperties properties = new BillingProperties();
        properties.setPaymentProvider("missing_provider");
        PaymentProviderResolver resolver = new PaymentProviderResolver(properties, List.of(provider("mock", List.of())));

        ApiException exception = assertThrows(ApiException.class, resolver::getActiveProvider);

        assertEquals("PAYMENT_PROVIDER_NOT_SUPPORTED", exception.getCode());
    }

    private PaymentProvider provider(String providerCode, List<String> aliases) {
        PaymentProvider provider = Mockito.mock(PaymentProvider.class);
        when(provider.getProviderCode()).thenReturn(providerCode);
        when(provider.getProviderAliases()).thenReturn(aliases);
        return provider;
    }
}
