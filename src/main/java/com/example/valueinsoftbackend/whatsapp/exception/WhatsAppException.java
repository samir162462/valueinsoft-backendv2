package com.example.valueinsoftbackend.whatsapp.exception;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;

public class WhatsAppException extends ApiException {

    private final String errorCategory;

    public WhatsAppException(HttpStatus status, String code, String errorCategory, String message) {
        super(status, code, message);
        this.errorCategory = errorCategory;
    }

    public WhatsAppException(HttpStatus status, String code, String errorCategory, String message, List<String> details) {
        super(status, code, message, details);
        this.errorCategory = errorCategory;
    }

    public String getErrorCategory() {
        return errorCategory;
    }
}
