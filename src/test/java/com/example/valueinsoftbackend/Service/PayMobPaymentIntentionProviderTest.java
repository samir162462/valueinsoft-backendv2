package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayMobPaymentIntentionProviderTest {

    @Test
    void paymentIntentionProviderFailsClosedUntilContractIsConfirmed() {
        PayMobPaymentIntentionProvider provider = new PayMobPaymentIntentionProvider();

        ApiException exception = assertThrows(ApiException.class, () ->
                provider.createProviderOrder(1001, 55, java.math.BigDecimal.TEN));

        assertEquals("paymob_intention", provider.getProviderCode());
        assertEquals("PAYMOB_INTENTION_CONTRACT_UNCONFIRMED", exception.getCode());
    }
}
