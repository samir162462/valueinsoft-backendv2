package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;

public class AiPermissionException extends ApiException {

    public AiPermissionException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
