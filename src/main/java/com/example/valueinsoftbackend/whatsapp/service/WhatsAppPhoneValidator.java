package com.example.valueinsoftbackend.whatsapp.service;

import com.example.valueinsoftbackend.whatsapp.exception.WhatsAppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class WhatsAppPhoneValidator {

    // Removes all non-digit characters
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("\\D");

    /**
     * Validates and normalizes the phone number.
     * Behind the scenes, if it's an Egyptian number starting with 01, it removes the 0 and prepends the default country code (e.g. 20).
     */
    public String validateAndNormalize(String phone, String defaultCountryCode) {
        if (phone == null || phone.isBlank()) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "INVALID_PHONE", "Phone number is required");
        }

        // Clean up any spaces, dashes, or plus signs
        String cleaned = NON_DIGIT_PATTERN.matcher(phone).replaceAll("");

        if (cleaned.isEmpty()) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "INVALID_PHONE", "Phone number must contain digits");
        }

        // Egyptian number normalization behind the scenes:
        // If it starts with "01" and has exactly 11 digits, it's a local Egyptian format
        if (cleaned.length() == 11 && cleaned.startsWith("01")) {
            // Remove leading '0' and prepend country code (default '20' if Egypt)
            String cc = (defaultCountryCode != null && !defaultCountryCode.isBlank()) ? defaultCountryCode.replace("+", "") : "20";
            cleaned = cc + cleaned.substring(1);
        } 
        // If it's already 12 digits and starts with "201", it's likely already formatted for Egypt
        else if (cleaned.length() == 12 && cleaned.startsWith("201")) {
            // Keep as is
        }
        // If the original string started with a '+' and it wasn't caught by the local check, we just use the digits
        
        // Basic length validation for general E.164 without '+'
        if (cleaned.length() < 10 || cleaned.length() > 15) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "INVALID_PHONE", "Phone number must be between 10 and 15 digits");
        }

        return cleaned; // Returning the raw digits string as WhatsApp Graph API requires "2010..." without '+'
    }
}
