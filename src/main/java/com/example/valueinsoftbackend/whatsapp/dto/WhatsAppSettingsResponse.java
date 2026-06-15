package com.example.valueinsoftbackend.whatsapp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsAppSettingsResponse {
    private long companyId;
    private boolean enabled;
    private String phoneNumberId;
    private String businessAccountId;
    private String accessTokenMasked; // Masked access token to show in frontend
    private String defaultCountryCode;
    private String graphApiVersion;
    private boolean hasToken; // Indicates whether a token is stored in the DB
}
