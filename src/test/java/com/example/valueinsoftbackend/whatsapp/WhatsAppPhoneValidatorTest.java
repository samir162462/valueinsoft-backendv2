package com.example.valueinsoftbackend.whatsapp;

import com.example.valueinsoftbackend.whatsapp.exception.WhatsAppException;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppPhoneValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppPhoneValidatorTest {

    private WhatsAppPhoneValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WhatsAppPhoneValidator();
    }

    @Test
    void testValidateAndNormalize_EgyptianLocalNumber() {
        // Behind the scenes it should remove the 0 and prepend 20
        String normalized = validator.validateAndNormalize("01012345678", "20");
        assertEquals("201012345678", normalized);
    }

    @Test
    void testValidateAndNormalize_EgyptianLocalNumberWithDifferentDefaultCountryCode() {
        String normalized = validator.validateAndNormalize("01012345678", "+20");
        assertEquals("201012345678", normalized);
    }

    @Test
    void testValidateAndNormalize_AlreadyEgyptianInternational() {
        String normalized = validator.validateAndNormalize("+201012345678", "20");
        assertEquals("201012345678", normalized);
        
        String normalizedNoPlus = validator.validateAndNormalize("201012345678", "20");
        assertEquals("201012345678", normalizedNoPlus);
    }

    @Test
    void testValidateAndNormalize_USNumber() {
        String normalized = validator.validateAndNormalize("+1-555-123-4567", "20");
        assertEquals("15551234567", normalized);
    }

    @Test
    void testValidateAndNormalize_InvalidTooShort() {
        assertThrows(WhatsAppException.class, () -> validator.validateAndNormalize("12345", "20"));
    }

    @Test
    void testValidateAndNormalize_InvalidTooLong() {
        assertThrows(WhatsAppException.class, () -> validator.validateAndNormalize("12345678901234567", "20"));
    }

    @Test
    void testValidateAndNormalize_NullOrEmpty() {
        assertThrows(WhatsAppException.class, () -> validator.validateAndNormalize(null, "20"));
        assertThrows(WhatsAppException.class, () -> validator.validateAndNormalize("", "20"));
        assertThrows(WhatsAppException.class, () -> validator.validateAndNormalize("   ", "20"));
    }
}
