package com.example.valueinsoftbackend.util;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

public final class RequestTimestampParser {

    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-M-d H:m:s")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    private RequestTimestampParser() {
    }

    public static Timestamp parse(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMESTAMP", fieldName + " is required");
        }

        String value = rawValue.trim();

        try {
            return Timestamp.from(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Timestamp.valueOf(LocalDateTime.parse(value.replace('T', ' '), LOCAL_TIMESTAMP_FORMATTER));
        } catch (DateTimeParseException ignored) {
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMESTAMP", fieldName + " has an unsupported format");
    }
}
