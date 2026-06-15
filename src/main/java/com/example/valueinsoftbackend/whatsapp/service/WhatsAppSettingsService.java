package com.example.valueinsoftbackend.whatsapp.service;

import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppSettingsRequest;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppSettingsResponse;
import com.example.valueinsoftbackend.whatsapp.model.WhatsAppSettings;
import com.example.valueinsoftbackend.whatsapp.repository.WhatsAppSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppSettingsService {

    private final WhatsAppSettingsRepository settingsRepository;
    private final WhatsAppTokenEncryptionService encryptionService;

    public WhatsAppSettingsService(WhatsAppSettingsRepository settingsRepository, WhatsAppTokenEncryptionService encryptionService) {
        this.settingsRepository = settingsRepository;
        this.encryptionService = encryptionService;
    }

    public WhatsAppSettingsResponse getSettings(long companyId) {
        WhatsAppSettings settings = settingsRepository.findByCompanyId(companyId);
        if (settings == null) {
            return WhatsAppSettingsResponse.builder()
                    .companyId(companyId)
                    .enabled(false)
                    .hasToken(false)
                    .build();
        }

        String maskedToken = maskToken(encryptionService.decrypt(settings.getAccessTokenEncrypted()));

        return WhatsAppSettingsResponse.builder()
                .companyId(companyId)
                .enabled(settings.isEnabled())
                .phoneNumberId(settings.getPhoneNumberId())
                .businessAccountId(settings.getBusinessAccountId())
                .defaultCountryCode(settings.getDefaultCountryCode())
                .graphApiVersion(settings.getGraphApiVersion())
                .hasToken(settings.getAccessTokenEncrypted() != null && !settings.getAccessTokenEncrypted().isEmpty())
                .accessTokenMasked(maskedToken)
                .build();
    }

    public WhatsAppSettingsResponse saveSettings(long companyId, WhatsAppSettingsRequest request) {
        WhatsAppSettings existing = settingsRepository.findByCompanyId(companyId);
        
        String encryptedToken = null;
        if (request.getAccessToken() != null && !request.getAccessToken().isBlank()) {
            encryptedToken = encryptionService.encrypt(request.getAccessToken());
        } else if (existing != null) {
            encryptedToken = existing.getAccessTokenEncrypted();
        }

        WhatsAppSettings settings = WhatsAppSettings.builder()
                .companyId(companyId)
                .enabled(request.isEnabled())
                .phoneNumberId(request.getPhoneNumberId())
                .businessAccountId(request.getBusinessAccountId())
                .accessTokenEncrypted(encryptedToken)
                .defaultCountryCode(request.getDefaultCountryCode())
                .graphApiVersion(request.getGraphApiVersion())
                .build();

        settingsRepository.upsert(settings);
        
        return getSettings(companyId);
    }
    
    public WhatsAppSettings getRawSettings(long companyId) {
        return settingsRepository.findByCompanyId(companyId);
    }

     String maskToken(String token) {
        if (token == null || token.length() <= 4) {
            return null;
        }
        return "••••••••••••" + token.substring(token.length() - 4);
    }
}
