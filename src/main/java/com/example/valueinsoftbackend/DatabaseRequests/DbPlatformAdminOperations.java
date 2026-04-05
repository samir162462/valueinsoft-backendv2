package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class DbPlatformAdminOperations {

    private static final RowMapper<BranchRuntimeStateRecord> BRANCH_RUNTIME_STATE_ROW_MAPPER = (rs, rowNum) ->
            new BranchRuntimeStateRecord(
                    rs.getInt("branch_id"),
                    rs.getInt("tenant_id"),
                    rs.getString("status"),
                    rs.getString("status_reason"),
                    rs.getTimestamp("locked_at"),
                    (Integer) rs.getObject("locked_by_user_id")
            );

    private final JdbcTemplate jdbcTemplate;

    public DbPlatformAdminOperations(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateTenantStatus(int tenantId, String newStatus) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        int rows = jdbcTemplate.update(
                "UPDATE public.tenants SET status = ?, updated_at = NOW() WHERE tenant_id = ?",
                newStatus,
                tenantId
        );
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }
    }

    public void ensureBranchRuntimeState(int branchId, int tenantId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        jdbcTemplate.update(
                "INSERT INTO public.branch_runtime_states (branch_id, tenant_id, status, status_reason) " +
                        "VALUES (?, ?, 'active', 'platform_admin_bootstrap') " +
                        "ON CONFLICT (branch_id) DO NOTHING",
                branchId,
                tenantId
        );
    }

    public BranchRuntimeStateRecord getBranchRuntimeState(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        List<BranchRuntimeStateRecord> results = jdbcTemplate.query(
                "SELECT branch_id, tenant_id, status, status_reason, locked_at, locked_by_user_id " +
                        "FROM public.branch_runtime_states WHERE branch_id = ?",
                BRANCH_RUNTIME_STATE_ROW_MAPPER,
                branchId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    public void upsertBranchRuntimeState(int branchId,
                                         int tenantId,
                                         String status,
                                         String statusReason,
                                         Integer lockedByUserId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        Timestamp lockedAt = "locked".equalsIgnoreCase(status) ? new Timestamp(System.currentTimeMillis()) : null;

        jdbcTemplate.update(
                "INSERT INTO public.branch_runtime_states " +
                        "(branch_id, tenant_id, status, status_reason, locked_at, locked_by_user_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (branch_id) DO UPDATE SET " +
                        "tenant_id = EXCLUDED.tenant_id, " +
                        "status = EXCLUDED.status, " +
                        "status_reason = EXCLUDED.status_reason, " +
                        "locked_at = EXCLUDED.locked_at, " +
                        "locked_by_user_id = EXCLUDED.locked_by_user_id, " +
                        "updated_at = NOW()",
                branchId,
                tenantId,
                status,
                statusReason,
                lockedAt,
                lockedByUserId
        );
    }

    public void insertTenantLifecycleEvent(int tenantId,
                                           String eventType,
                                           String previousStatus,
                                           String newStatus,
                                           String reason,
                                           String note,
                                           Integer actorUserId,
                                           String actorUserName,
                                           String source) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        jdbcTemplate.update(
                "INSERT INTO public.tenant_lifecycle_events " +
                        "(tenant_id, event_type, previous_status, new_status, reason, note, actor_user_id, actor_user_name, source) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId,
                eventType,
                previousStatus,
                newStatus,
                reason,
                note,
                actorUserId,
                actorUserName,
                source
        );
    }

    public void insertBranchLifecycleEvent(int tenantId,
                                           int branchId,
                                           String eventType,
                                           String previousStatus,
                                           String newStatus,
                                           String reason,
                                           String note,
                                           Integer actorUserId,
                                           String actorUserName,
                                           String source) {
        TenantSqlIdentifiers.requirePositive(tenantId, "tenantId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        jdbcTemplate.update(
                "INSERT INTO public.branch_lifecycle_events " +
                        "(tenant_id, branch_id, event_type, previous_status, new_status, reason, note, actor_user_id, actor_user_name, source) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId,
                branchId,
                eventType,
                previousStatus,
                newStatus,
                reason,
                note,
                actorUserId,
                actorUserName,
                source
        );
    }

    public void insertAuditLog(Integer actorUserId,
                               String actorUserName,
                               String capabilityKey,
                               String actionType,
                               Integer targetTenantId,
                               Integer targetBranchId,
                               String requestSummaryJson,
                               String contextSummaryJson,
                               String resultStatus,
                               String correlationId) {
        jdbcTemplate.update(
                "INSERT INTO public.platform_admin_audit_log " +
                        "(actor_user_id, actor_user_name, capability_key, action_type, target_tenant_id, target_branch_id, " +
                        "request_summary, context_summary, result_status, correlation_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)",
                actorUserId,
                actorUserName,
                capabilityKey,
                actionType,
                targetTenantId,
                targetBranchId,
                requestSummaryJson == null ? "{}" : requestSummaryJson,
                contextSummaryJson == null ? "{}" : contextSummaryJson,
                resultStatus,
                correlationId
        );
    }

    public static class BranchRuntimeStateRecord {
        private final int branchId;
        private final int tenantId;
        private final String status;
        private final String statusReason;
        private final Timestamp lockedAt;
        private final Integer lockedByUserId;

        public BranchRuntimeStateRecord(int branchId,
                                        int tenantId,
                                        String status,
                                        String statusReason,
                                        Timestamp lockedAt,
                                        Integer lockedByUserId) {
            this.branchId = branchId;
            this.tenantId = tenantId;
            this.status = status;
            this.statusReason = statusReason;
            this.lockedAt = lockedAt;
            this.lockedByUserId = lockedByUserId;
        }

        public int getBranchId() {
            return branchId;
        }

        public int getTenantId() {
            return tenantId;
        }

        public String getStatus() {
            return status;
        }

        public String getStatusReason() {
            return statusReason;
        }

        public Timestamp getLockedAt() {
            return lockedAt;
        }

        public Integer getLockedByUserId() {
            return lockedByUserId;
        }
    }
}
