package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAlertAcknowledgmentItem {
    private long acknowledgmentId;
    private String alertKey;
    private Integer targetTenantId;
    private Integer targetBranchId;
    private String note;
    private Integer acknowledgedByUserId;
    private String acknowledgedByUserName;
    private Timestamp acknowledgedAt;
    private Timestamp expiresAt;
    private Timestamp clearedAt;
    private Integer clearedByUserId;
    private String clearedByUserName;
    private boolean active;
}
