package com.example.valueinsoftbackend.whatsapp.controller;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppMessageLogResponse;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppSettingsRequest;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppSettingsResponse;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppTestSendRequest;
import com.example.valueinsoftbackend.whatsapp.dto.WhatsAppTestSendResponse;
import com.example.valueinsoftbackend.whatsapp.model.WhatsAppMessageLog;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppService;
import com.example.valueinsoftbackend.whatsapp.service.WhatsAppSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppAdminController {

    private final WhatsAppSettingsService settingsService;
    private final WhatsAppService whatsappService;
    private final AuthorizationService authorizationService;

    public WhatsAppAdminController(WhatsAppSettingsService settingsService,
                                   WhatsAppService whatsappService,
                                   AuthorizationService authorizationService) {
        this.settingsService = settingsService;
        this.whatsappService = whatsappService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/settings/{companyId}")
    public ResponseEntity<WhatsAppSettingsResponse> getSettings(@PathVariable long companyId, Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), (int) companyId, null, "whatsapp.settings.view");
        return ResponseEntity.ok(settingsService.getSettings(companyId));
    }

    @PutMapping("/settings/{companyId}")
    public ResponseEntity<WhatsAppSettingsResponse> saveSettings(@PathVariable long companyId,
                                                                 @Valid @RequestBody WhatsAppSettingsRequest request,
                                                                 Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), (int) companyId, null, "whatsapp.settings.manage");
        return ResponseEntity.ok(settingsService.saveSettings(companyId, request));
    }

    @PostMapping("/test-send/{companyId}")
    public ResponseEntity<WhatsAppTestSendResponse> sendTestMessage(@PathVariable long companyId,
                                                                    @Valid @RequestBody WhatsAppTestSendRequest request,
                                                                    Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), (int) companyId, null, "whatsapp.send.test");
        
        try {
            WhatsAppMessageLog log = whatsappService.sendText(companyId, request.getToPhone(), request.getMessage());
            boolean success = "SENT".equals(log.getStatus());
            return ResponseEntity.ok(WhatsAppTestSendResponse.builder()
                    .success(success)
                    .message(success ? "Message sent successfully" : "Failed to send message: " + log.getErrorMessage())
                    .providerMessageId(log.getProviderMessageId())
                    .errorCode(log.getErrorCode())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(WhatsAppTestSendResponse.builder()
                    .success(false)
                    .message("Failed to send message: " + e.getMessage())
                    .errorCode("EXCEPTION")
                    .build());
        }
    }

    @GetMapping("/message-logs/{companyId}")
    public ResponseEntity<List<WhatsAppMessageLogResponse>> getMessageLogs(@PathVariable long companyId,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size,
                                                                           Principal principal) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), (int) companyId, null, "whatsapp.logs.view");
        return ResponseEntity.ok(whatsappService.getLogs(companyId, page, size));
    }
}
