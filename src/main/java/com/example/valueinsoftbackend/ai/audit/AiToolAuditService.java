package com.example.valueinsoftbackend.ai.audit;

import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class AiToolAuditService {

    private static final int MAX_SUMMARY_LENGTH = 1000;

    private final AiToolAuditRepository repository;
    private final AiPermissionService permissionService;
    private final ObjectMapper objectMapper;

    public AiToolAuditService(AiToolAuditRepository repository,
                              AiPermissionService permissionService,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public void logToolCall(UUID conversationId,
                            long companyId,
                            Long branchId,
                            long userId,
                            String toolName,
                            Object input,
                            String outputSummary,
                            boolean success,
                            String errorMessage,
                            Long durationMs) {
        try {
            repository.create(
                    conversationId,
                    companyId,
                    branchId,
                    userId,
                    safeText(toolName, 150),
                    safeJson(input),
                    safeText(outputSummary, MAX_SUMMARY_LENGTH),
                    success,
                    safeText(errorMessage, 500),
                    durationMs
            );
            log.debug("AI tool audit stored conversationId={} companyId={} branchId={} userId={} tool={} success={} durationMs={}",
                    conversationId,
                    companyId,
                    branchId,
                    userId,
                    toolName,
                    success,
                    durationMs);
        } catch (RuntimeException exception) {
            log.warn("Failed to write AI tool audit row for tool {}", toolName, exception);
        }
    }

    private String safeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return safeText(permissionService.maskSensitiveData(objectMapper.writeValueAsString(value)), 2000);
        } catch (JsonProcessingException exception) {
            return "{\"summary\":\"unserializable input\"}";
        }
    }

    private String safeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String masked = permissionService.maskSensitiveData(value).trim();
        return masked.length() <= maxLength ? masked : masked.substring(0, maxLength);
    }
}
