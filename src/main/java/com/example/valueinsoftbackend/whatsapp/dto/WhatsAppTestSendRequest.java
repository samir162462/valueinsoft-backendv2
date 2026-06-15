package com.example.valueinsoftbackend.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WhatsAppTestSendRequest {
    @NotBlank(message = "Recipient phone is required")
    private String toPhone;
    
    @NotBlank(message = "Message body is required")
    private String message;
}
