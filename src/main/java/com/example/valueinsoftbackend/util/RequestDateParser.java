package com.example.valueinsoftbackend.util;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;

import java.sql.Date;
import java.time.LocalDate;

public final class RequestDateParser {

    private RequestDateParser() {
    }

    public static Date parseSqlDate(String value, String fieldName) {
        try {
            return Date.valueOf(LocalDate.parse(value));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", fieldName + " must be in yyyy-MM-dd format");
        }
    }
}
