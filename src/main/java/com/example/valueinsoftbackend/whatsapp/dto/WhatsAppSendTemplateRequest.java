package com.example.valueinsoftbackend.whatsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppSendTemplateRequest {
    private String toPhone;
    private String templateName;
    private String languageCode;
    private List<Map<String, String>> parameters;
}
