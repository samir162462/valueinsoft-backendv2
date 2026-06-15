package com.example.valueinsoftbackend.whatsapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppMessageLog {
    private Long logId;
    private long companyId;
    private String recipientPhone;
    private String messageType; // TEXT, TEMPLATE, MEDIA
    private String templateName;
    private String languageCode;
    private String messageBody;
    private String providerMessageId;
    private String status; // PENDING, SENT, DELIVERED, FAILED, READ
    private String errorCode;
    private String errorMessage;
    private String requestPayload;
    private String responsePayload;
    private Long latencyMs;
    private Instant createdAt;
    private Instant updatedAt;
}
