package com.example.valueinsoftbackend.whatsapp.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class WhatsAppMessageLogResponse {
    private Long logId;
    private String recipientPhoneMasked; // Masks part of the phone number for security
    private String messageType;
    private String templateName;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
}
