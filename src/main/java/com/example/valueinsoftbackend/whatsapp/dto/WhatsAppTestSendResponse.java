package com.example.valueinsoftbackend.whatsapp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsAppTestSendResponse {
    private boolean success;
    private String message;
    private String providerMessageId;
    private String errorCode;
}
