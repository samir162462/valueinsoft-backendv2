package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceAudit;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class FinanceAuditService {

    private final DbFinanceAudit dbFinanceAudit;
    private final DbUsers dbUsers;
    private final ObjectMapper objectMapper;

    public FinanceAuditService(DbFinanceAudit dbFinanceAudit,
                               DbUsers dbUsers,
                               ObjectMapper objectMapper) {
        this.dbFinanceAudit = dbFinanceAudit;
        this.dbUsers = dbUsers;
        this.objectMapper = objectMapper;
    }

    public String recordEvent(String authenticatedName,
                              int companyId,
                              Integer branchId,
                              String eventType,
                              String entityType,
                              String entityId,
                              Map<String, Object> afterState,
                              String reason) {
        String correlationId = UUID.randomUUID().toString();
        dbFinanceAudit.createAuditEvent(
                companyId,
                branchId,
                resolveActorUserId(authenticatedName),
                eventType,
                entityType,
                entityId,
                null,
                toJson(afterState),
                reason,
                correlationId);
        return correlationId;
    }

    public Integer resolveActorUserId(String authenticatedName) {
        String baseUserName = extractBaseUserName(authenticatedName);
        if (baseUserName.isBlank()) {
            return null;
        }
        User user = dbUsers.getUser(baseUserName);
        return user == null ? null : user.getUserId();
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true}";
        }
    }
}
