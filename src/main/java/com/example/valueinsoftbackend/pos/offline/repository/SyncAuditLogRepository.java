package com.example.valueinsoftbackend.pos.offline.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

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
                INSERT INTO pos_sync_audit_log
                    (company_id, branch_id, sync_batch_id, offline_order_import_id,
                     device_id, cashier_id, event_type, event_message, payload_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, companyId, branchId, syncBatchId, offlineOrderImportId,
                deviceId, cashierId, eventType, eventMessage, payloadJson);
    }
}
