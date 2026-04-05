package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAuditEventItem {
    private long eventId;
    private Integer actorUserId;
    private String actorUserName;
    private String capabilityKey;
    private String actionType;
    private Integer targetTenantId;
    private Integer targetBranchId;
    private String requestSummaryJson;
    private String contextSummaryJson;
    private String resultStatus;
    private String correlationId;
    private Timestamp createdAt;
}
