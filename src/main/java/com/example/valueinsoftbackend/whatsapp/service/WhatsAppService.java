package com.example.valueinsoftbackend.whatsapp.service;

import com.example.valueinsoftbackend.whatsapp.config.WhatsAppProperties;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppMessageLogResponse;
import com.example.valueinsoftbackend.whatsapp.exception.WhatsAppException;
import com.example.valueinsoftbackend.whatsapp.model.WhatsAppMessageLog;
import com.example.valueinsoftbackend.whatsapp.model.WhatsAppSettings;
import com.example.valueinsoftbackend.whatsapp.repository.WhatsAppMessageLogRepository;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppClient.WhatsAppResponse;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WhatsAppService {

    private final WhatsAppSettingsService settingsService;
    private final WhatsAppTokenEncryptionService encryptionService;
    private final WhatsAppPhoneValidator phoneValidator;
    private final WhatsAppClient client;
    private final WhatsAppMessageLogRepository logRepository;
    private final WhatsAppProperties properties;
    private final Gson gson;

    public WhatsAppService(WhatsAppSettingsService settingsService,
                           WhatsAppTokenEncryptionService encryptionService,
                           WhatsAppPhoneValidator phoneValidator,
                           WhatsAppClient client,
                           WhatsAppMessageLogRepository logRepository,
                           WhatsAppProperties properties,
                           Gson gson) {
        this.settingsService = settingsService;
        this.encryptionService = encryptionService;
        this.phoneValidator = phoneValidator;
        this.client = client;
        this.logRepository = logRepository;
        this.properties = properties;
        this.gson = gson;
    }

    public WhatsAppMessageLog sendText(long companyId, String toPhone, String messageBody) {
        if (messageBody == null || messageBody.isBlank()) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "INVALID_MESSAGE", "Message body cannot be empty");
        }
        if (messageBody.length() > properties.getMaxMessageBodyLength()) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "INVALID_MESSAGE", "Message body exceeds maximum length");
        }

        WhatsAppSettings settings = validateAndGetSettings(companyId);
        String targetPhone = phoneValidator.validateAndNormalize(toPhone, settings.getDefaultCountryCode());

        Map<String, Object> textObj = new HashMap<>();
        textObj.put("body", messageBody);

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", targetPhone);
        payload.put("type", "text");
        payload.put("text", textObj);

        WhatsAppMessageLog logEntry = logRepository.insert(WhatsAppMessageLog.builder()
                .companyId(companyId)
                .recipientPhone(targetPhone)
                .messageType("TEXT")
                .messageBody(messageBody)
                .status("PENDING")
                .build());

        return executeSend(logEntry, payload, settings);
    }

    public WhatsAppMessageLog sendTemplate(long companyId, String toPhone, String templateName, String languageCode, List<Map<String, String>> parameters) {
        if (templateName == null || templateName.isBlank() || languageCode == null || languageCode.isBlank()) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE", "INVALID_TEMPLATE", "Template name and language code are required");
        }

        WhatsAppSettings settings = validateAndGetSettings(companyId);
        String targetPhone = phoneValidator.validateAndNormalize(toPhone, settings.getDefaultCountryCode());

        Map<String, Object> languageObj = new HashMap<>();
        languageObj.put("code", languageCode);

        Map<String, Object> templateObj = new HashMap<>();
        templateObj.put("name", templateName);
        templateObj.put("language", languageObj);
        if (parameters != null && !parameters.isEmpty()) {
            templateObj.put("components", parameters);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", targetPhone);
        payload.put("type", "template");
        payload.put("template", templateObj);

        WhatsAppMessageLog logEntry = logRepository.insert(WhatsAppMessageLog.builder()
                .companyId(companyId)
                .recipientPhone(targetPhone)
                .messageType("TEMPLATE")
                .templateName(templateName)
                .languageCode(languageCode)
                .status("PENDING")
                .build());

        return executeSend(logEntry, payload, settings);
    }

    private WhatsAppMessageLog executeSend(WhatsAppMessageLog logEntry, Object payload, WhatsAppSettings settings) {
        String accessToken = encryptionService.decrypt(settings.getAccessTokenEncrypted());
        
        int attempts = 0;
        long delay = properties.getRetryInitialDelayMs();
        WhatsAppResponse response = null;

        while (attempts < properties.getMaxRetryAttempts()) {
            attempts++;
            response = client.sendRequest(settings.getGraphApiVersion(), settings.getPhoneNumberId(), accessToken, payload);

            if (response.success || !isRetryable(response.errorCategory)) {
                break;
            }

            if (attempts < properties.getMaxRetryAttempts()) {
                log.warn("WhatsApp API call failed. Retrying... Attempt {}/{}", attempts, properties.getMaxRetryAttempts());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                delay *= 2; // Exponential backoff
            }
        }

        return updateLogEntry(logEntry, response);
    }

    private boolean isRetryable(String errorCategory) {
        return "SERVER_ERROR".equals(errorCategory) || "NETWORK_ERROR".equals(errorCategory);
    }

    private WhatsAppMessageLog updateLogEntry(WhatsAppMessageLog logEntry, WhatsAppResponse response) {
        String providerMessageId = null;

        if (response.success) {
            try {
                Map<?, ?> responseMap = gson.fromJson(response.responseBody, Map.class);
                if (responseMap != null && responseMap.containsKey("messages")) {
                    List<?> messages = (List<?>) responseMap.get("messages");
                    if (!messages.isEmpty()) {
                        Map<?, ?> firstMessage = (Map<?, ?>) messages.get(0);
                        providerMessageId = (String) firstMessage.get("id");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse provider message ID from success response", e);
            }
        }

        logRepository.updateStatus(
                logEntry.getLogId(),
                response.success ? "SENT" : "FAILED",
                providerMessageId,
                response.success ? null : response.errorCategory,
                response.success ? null : response.errorMessage,
                response.responseBody,
                response.latencyMs
        );

        logEntry.setStatus(response.success ? "SENT" : "FAILED");
        logEntry.setProviderMessageId(providerMessageId);
        logEntry.setErrorCode(response.success ? null : response.errorCategory);
        logEntry.setErrorMessage(response.success ? null : response.errorMessage);
        logEntry.setResponsePayload(response.responseBody);
        logEntry.setLatencyMs(response.latencyMs);

        return logEntry;
    }

    private WhatsAppSettings validateAndGetSettings(long companyId) {
        WhatsAppSettings settings = settingsService.getRawSettings(companyId);
        if (settings == null) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "SETTINGS_NOT_FOUND", "SETTINGS_NOT_FOUND", "WhatsApp settings not found for this company");
        }
        if (!settings.isEnabled()) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "WHATSAPP_DISABLED", "WHATSAPP_DISABLED", "WhatsApp is disabled for this company");
        }
        if (settings.getAccessTokenEncrypted() == null || settings.getPhoneNumberId() == null) {
            throw new WhatsAppException(HttpStatus.BAD_REQUEST, "INCOMPLETE_SETTINGS", "INCOMPLETE_SETTINGS", "WhatsApp settings are incomplete");
        }
        return settings;
    }

    public List<WhatsAppMessageLogResponse> getLogs(long companyId, int page, int size) {
        int offset = page * size;
        return logRepository.findByCompanyId(companyId, offset, size).stream()
                .map(this::mapToLogResponse)
                .collect(Collectors.toList());
    }

    private WhatsAppMessageLogResponse mapToLogResponse(WhatsAppMessageLog log) {
        String phone = log.getRecipientPhone();
        String maskedPhone = phone;
        if (phone != null && phone.length() > 4) {
            maskedPhone = phone.substring(0, phone.length() - 4).replaceAll(".", "*") + phone.substring(phone.length() - 4);
        }

        return WhatsAppMessageLogResponse.builder()
                .logId(log.getLogId())
                .recipientPhoneMasked(maskedPhone)
                .messageType(log.getMessageType())
                .templateName(log.getTemplateName())
                .status(log.getStatus())
                .errorCode(log.getErrorCode())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
