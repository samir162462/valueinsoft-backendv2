package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAlertNotificationOutboxItem {
    private long notificationId;
    private String alertKey;
    private Integer targetTenantId;
    private Integer targetBranchId;
    private String eventType;
    private String payloadJson;
    private String status;
    private int attemptCount;
    private Integer requestedByUserId;
    private String requestedByUserName;
    private Timestamp createdAt;
    private Timestamp processedAt;
    private String lastError;
}
