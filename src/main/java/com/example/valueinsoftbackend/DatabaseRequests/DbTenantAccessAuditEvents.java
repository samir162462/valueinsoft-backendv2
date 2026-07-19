package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Append-only audit writer for tenant user-access override mutations.
 */
@Repository
public class DbTenantAccessAuditEvents {

    private final JdbcTemplate jdbcTemplate;

    public DbTenantAccessAuditEvents(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertEvent(int tenantId,
                            int actorUserId,
                            int targetUserId,
                            String capabilityKey,
                            String action,
                            String grantMode,
                            String scopeType,
                            Integer scopeBranchId,
                            String reason) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(actorUserId, "actorUserId");
        TenantSqlIdentifiers.requirePositive(targetUserId, "targetUserId");
        if (scopeBranchId != null) {
            TenantSqlIdentifiers.requirePositive(scopeBranchId, "scopeBranchId");
        }

        jdbcTemplate.update(
                "INSERT INTO public.tenant_access_audit_events " +
                        "(tenant_id, actor_user_id, target_user_id, capability_key, action, grant_mode, " +
                        "scope_type, scope_branch_id, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId,
                actorUserId,
                targetUserId,
                capabilityKey,
                action,
                grantMode,
                scopeType,
                scopeBranchId,
                reason
        );
    }
}

