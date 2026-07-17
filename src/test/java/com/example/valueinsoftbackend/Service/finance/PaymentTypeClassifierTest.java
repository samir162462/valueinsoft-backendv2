package com.example.valueinsoftbackend.Service.finance;

import org.junit.jupiter.api.Test;

import static com.example.valueinsoftbackend.Service.finance.PaymentTypeClassifier.Category.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentTypeClassifierTest {

    @Test
    void classifiesKnownLegacyAndModernValues() {
        assertEquals(CASH, PaymentTypeClassifier.classify("Dirict").category());
        assertEquals(CASH, PaymentTypeClassifier.classify("مباشر").category());
        assertEquals(RECEIVABLE, PaymentTypeClassifier.classify("CREDIT").category());
        assertEquals(RECEIVABLE, PaymentTypeClassifier.classify("receivable").category());
        assertEquals(CARD, PaymentTypeClassifier.classify("Visa").category());
        assertEquals(WALLET, PaymentTypeClassifier.classify("Vodafone Cash").category());
    }

    @Test
    void preservesUnknownRawValueWithoutSubstringInference() {
        PaymentTypeClassifier.Classification classification =
                PaymentTypeClassifier.classify("Corporate Cashier Account");

        assertEquals(OTHER, classification.category());
        assertEquals("corporate cashier account", classification.mappingKey());
    }
}
