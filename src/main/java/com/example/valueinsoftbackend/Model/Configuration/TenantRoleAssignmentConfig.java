package com.example.valueinsoftbackend.Model.Configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantRoleAssignmentConfig {
    private long assignmentId;
    private int tenantId;
    private int userId;
    private String roleId;
    private String status;
    private Timestamp assignedAt;
    private Integer assignedByUserId;
    private String source;
    private String scopeType;
    private Integer scopeBranchId;
}
