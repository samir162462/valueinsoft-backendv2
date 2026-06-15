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
public class WhatsAppSettings {
    private Long settingsId;
    private long companyId;
    private boolean enabled;
    private String phoneNumberId;
    private String businessAccountId;
    private String accessTokenEncrypted;
    private String defaultCountryCode;
    private String graphApiVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
