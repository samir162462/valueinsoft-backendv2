package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminImportAuditEvent;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineAdminRecentEvent;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@Slf4j
public class SyncAuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public SyncAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAuditLog(Long companyId, Long branchId, Long syncBatchId,
                               Long offlineOrderImportId, Long deviceId, Long cashierId,
                               String eventType, String eventMessage, String payloadJson) {
        String sql = """
                INSERT INTO %s
                    (company_id, branch_id, sync_batch_id, offline_order_import_id,
                     device_id, cashier_id, event_type, event_message, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """.formatted(TenantSqlIdentifiers.posSyncAuditLogTable(companyId));
        jdbcTemplate.update(sql, companyId, branchId, syncBatchId, offlineOrderImportId,
                deviceId, cashierId, eventType, eventMessage, payloadJson);
    }

    public List<OfflineAdminRecentEvent> findRecentAdminEvents(Long companyId, Long branchId, Long batchId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 25));
        String sql = """
                SELECT
                    event_type,
                    event_message,
                    payload_json ->> 'reason' AS reason,
                    created_at
                FROM %s
                WHERE company_id = ?
                  AND branch_id = ?
                  AND sync_batch_id = ?
                  AND event_type LIKE 'OFFLINE_ADMIN_%%'
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """.formatted(TenantSqlIdentifiers.posSyncAuditLogTable(companyId));
        return jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineAdminRecentEvent(
                rs.getString("event_type"),
                rs.getTimestamp("created_at").toInstant(),
                actorFromMessage(rs.getString("event_message")),
                rs.getString("reason"),
                rs.getString("event_type") != null && rs.getString("event_type").endsWith("_BLOCKED")
        ), companyId, branchId, batchId, safeLimit);
    }

    public List<OfflineAdminImportAuditEvent> findRecentImportEvents(Long companyId, Long branchId,
                                                                    Long offlineOrderImportId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 25));
        String sql = """
                SELECT
                    event_type,
                    event_message,
                    payload_json ->> 'reason' AS reason,
                    created_at
                FROM %s
                WHERE company_id = ?
                  AND branch_id = ?
                  AND offline_order_import_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """.formatted(TenantSqlIdentifiers.posSyncAuditLogTable(companyId));
        return jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineAdminImportAuditEvent(
                rs.getString("event_type"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                actorFromMessage(rs.getString("event_message")),
                rs.getString("reason")
        ), companyId, branchId, offlineOrderImportId, safeLimit);
    }

    private String actorFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String marker = " by ";
        int index = message.lastIndexOf(marker);
        if (index < 0 || index + marker.length() >= message.length()) {
            return null;
        }
        return message.substring(index + marker.length()).trim();
    }
}
