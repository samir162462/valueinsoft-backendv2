package com.example.valueinsoftbackend.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WhatsAppSettingsRequest {
    private boolean enabled;
    
    @NotBlank(message = "Phone Number ID is required")
    private String phoneNumberId;
    
    @NotBlank(message = "Business Account ID is required")
    private String businessAccountId;
    
    // Can be null if the user is not updating the token
    private String accessToken;
    
    private String defaultCountryCode = "20"; // Defaulting to Egypt as per requirements without +
    private String graphApiVersion = "v21.0";
}
