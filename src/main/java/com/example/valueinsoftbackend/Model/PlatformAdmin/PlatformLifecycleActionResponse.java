package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformLifecycleActionResponse {
    private String targetType;
    private int tenantId;
    private Integer branchId;
    private String action;
    private String previousStatus;
    private String newStatus;
    private boolean changed;
    private String reason;
    private String note;
    private String actorUserName;
    private Timestamp processedAt;
}
