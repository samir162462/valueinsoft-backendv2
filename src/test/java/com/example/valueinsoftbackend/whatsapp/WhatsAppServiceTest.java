package com.example.valueinsoftbackend.whatsapp;

import com.example.valueinsoftbackend.whatsapp.config.WhatsAppProperties;
import com.example.valueinsoftbackend.whatsapp.exception.WhatsAppException;
import com.example.valueinsoftbackend.whatsapp.model.WhatsAppMessageLog;
import com.example.valueinsoftbackend.whatsapp.model.WhatsAppSettings;
import com.example.valueinsoftbackend.whatsapp.repository.WhatsAppMessageLogRepository;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppClient;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppPhoneValidator;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppService;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppSettingsService;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppTokenEncryptionService;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppServiceTest {

    private WhatsAppSettingsService settingsService;
    private WhatsAppTokenEncryptionService encryptionService;
    private WhatsAppPhoneValidator phoneValidator;
    private WhatsAppClient client;
    private WhatsAppMessageLogRepository logRepository;
    private WhatsAppProperties properties;
    private Gson gson;

    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        settingsService = mock(WhatsAppSettingsService.class);
        encryptionService = mock(WhatsAppTokenEncryptionService.class);
        phoneValidator = mock(WhatsAppPhoneValidator.class);
        client = mock(WhatsAppClient.class);
        logRepository = mock(WhatsAppMessageLogRepository.class);
        properties = mock(WhatsAppProperties.class);
        gson = new Gson();

        when(properties.getMaxMessageBodyLength()).thenReturn(4096);
        when(properties.getMaxRetryAttempts()).thenReturn(1);
        when(properties.getRetryInitialDelayMs()).thenReturn(100L);

        service = new WhatsAppService(
                settingsService, encryptionService, phoneValidator, client, logRepository, properties, gson
        );
    }

    @Test
    void testSendText_Success() {
        long companyId = 1L;
        String phone = "01012345678";
        String normalizedPhone = "201012345678";
        String message = "Hello Test";

        WhatsAppSettings settings = WhatsAppSettings.builder()
                .companyId(companyId)
                .enabled(true)
                .phoneNumberId("pn123")
                .accessTokenEncrypted("encrypted_token")
                .defaultCountryCode("20")
                .graphApiVersion("v21.0")
                .build();

        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                .logId(10L)
                .companyId(companyId)
                .recipientPhone(normalizedPhone)
                .messageType("TEXT")
                .status("PENDING")
                .build();

        when(settingsService.getRawSettings(companyId)).thenReturn(settings);
        when(phoneValidator.validateAndNormalize(phone, "20")).thenReturn(normalizedPhone);
        when(encryptionService.decrypt("encrypted_token")).thenReturn("real_token");
        when(logRepository.insert(any(WhatsAppMessageLog.class))).thenReturn(logEntry);

        String successResponseBody = "{\"messages\":[{\"id\":\"wamid.123\"}]}";
        WhatsAppClient.WhatsAppResponse clientResponse = new WhatsAppClient.WhatsAppResponse(
                true, successResponseBody, null, null, "{}", 120L
        );

        when(client.sendRequest(eq("v21.0"), eq("pn123"), eq("real_token"), any())).thenReturn(clientResponse);

        WhatsAppMessageLog result = service.sendText(companyId, phone, message);

        assertNotNull(result);
        assertEquals("SENT", result.getStatus());
        assertEquals("wamid.123", result.getProviderMessageId());

        verify(logRepository).updateStatus(eq(10L), eq("SENT"), eq("wamid.123"), eq(null), eq(null), eq(successResponseBody), eq(120L));
    }

    @Test
    void testSendText_DisabledSettings() {
        long companyId = 1L;
        WhatsAppSettings settings = WhatsAppSettings.builder()
                .companyId(companyId)
                .enabled(false)
                .build();

        when(settingsService.getRawSettings(companyId)).thenReturn(settings);

        WhatsAppException ex = assertThrows(WhatsAppException.class, () -> service.sendText(companyId, "01012345678", "Hello"));
        assertEquals("WHATSAPP_DISABLED", ex.getErrorCategory());
    }

    @Test
    void testSendTemplate_Success() {
        long companyId = 1L;
        String phone = "01012345678";
        String normalizedPhone = "201012345678";

        WhatsAppSettings settings = WhatsAppSettings.builder()
                .companyId(companyId)
                .enabled(true)
                .phoneNumberId("pn123")
                .accessTokenEncrypted("encrypted_token")
                .defaultCountryCode("20")
                .graphApiVersion("v21.0")
                .build();

        WhatsAppMessageLog logEntry = WhatsAppMessageLog.builder()
                .logId(11L)
                .companyId(companyId)
                .recipientPhone(normalizedPhone)
                .messageType("TEMPLATE")
                .status("PENDING")
                .build();

        when(settingsService.getRawSettings(companyId)).thenReturn(settings);
        when(phoneValidator.validateAndNormalize(phone, "20")).thenReturn(normalizedPhone);
        when(encryptionService.decrypt("encrypted_token")).thenReturn("real_token");
        when(logRepository.insert(any(WhatsAppMessageLog.class))).thenReturn(logEntry);

        String successResponseBody = "{\"messages\":[{\"id\":\"wamid.456\"}]}";
        WhatsAppClient.WhatsAppResponse clientResponse = new WhatsAppClient.WhatsAppResponse(
                true, successResponseBody, null, null, "{}", 100L
        );

        when(client.sendRequest(eq("v21.0"), eq("pn123"), eq("real_token"), any())).thenReturn(clientResponse);

        WhatsAppMessageLog result = service.sendTemplate(companyId, phone, "hello_world", "en_US", Collections.emptyList());

        assertNotNull(result);
        assertEquals("SENT", result.getStatus());
        assertEquals("wamid.456", result.getProviderMessageId());
    }
}
