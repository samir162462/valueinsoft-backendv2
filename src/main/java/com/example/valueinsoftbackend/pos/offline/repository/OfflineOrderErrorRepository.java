package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorItemResponse;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Slf4j
public class OfflineOrderErrorRepository {

    private final JdbcTemplate jdbcTemplate;

    public OfflineOrderErrorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------
    // RowMapper — maps directly to SyncErrorResponse DTO
    // -------------------------------------------------------

    private static final RowMapper<SyncErrorResponse> ERROR_RESPONSE_MAPPER = (rs, rowNum) -> new SyncErrorResponse(
            rs.getLong("id"),
            rs.getLong("offline_order_import_id"),
            rs.getString("error_stage"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getString("field_path"),
            rs.getString("raw_value"),
            OfflineErrorSeverity.valueOf(rs.getString("severity")),
            rs.getBoolean("retry_allowed"),
            rs.getBoolean("manager_review_required"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<SyncErrorItemResponse> ERROR_ITEM_MAPPER = (rs, rowNum) -> new SyncErrorItemResponse(
            rs.getLong("id"),
            rs.getLong("offline_order_import_id"),
            rs.getString("offline_order_no"),
            rs.getString("error_stage"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getString("field_path"),
            OfflineErrorSeverity.valueOf(rs.getString("severity")),
            rs.getBoolean("retry_allowed"),
            rs.getBoolean("manager_review_required"),
            rs.getTimestamp("created_at").toInstant()
    );

    // -------------------------------------------------------
    // Insert
    // -------------------------------------------------------

    public Long insertError(Long offlineOrderImportId, Long companyId, Long branchId,
                            String errorStage, String errorCode, String errorMessage,
                            String fieldPath, String rawValue,
                            OfflineErrorSeverity severity,
                            boolean retryAllowed, boolean managerReviewRequired) {
        String sql = """
                INSERT INTO %s
                    (offline_order_import_id, company_id, branch_id,
                     error_stage, error_code, error_message,
                     field_path, raw_value, severity,
                     retry_allowed, manager_review_required)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.formatted(TenantSqlIdentifiers.posOfflineOrderErrorTable(companyId));
        return jdbcTemplate.queryForObject(sql, Long.class,
                offlineOrderImportId, companyId, branchId,
                errorStage, errorCode, errorMessage,
                fieldPath, rawValue, severity.name(),
                retryAllowed, managerReviewRequired);
    }

    // -------------------------------------------------------
    // Lookups
    // -------------------------------------------------------

    public List<SyncErrorResponse> findErrorsByBatchId(Long companyId, Long branchId, Long syncBatchId) {
        String sql = """
                SELECT e.* FROM %s e
                JOIN %s oi ON oi.id = e.offline_order_import_id
                WHERE oi.sync_batch_id = ?
                  AND oi.company_id = ?
                  AND oi.branch_id = ?
                  AND e.company_id = ?
                  AND e.branch_id = ?
                ORDER BY e.created_at ASC
                """.formatted(
                TenantSqlIdentifiers.posOfflineOrderErrorTable(companyId),
                TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        return jdbcTemplate.query(sql, ERROR_RESPONSE_MAPPER, syncBatchId, companyId, branchId, companyId, branchId);
    }

    public List<SyncErrorItemResponse> findErrorsByBatchId(Long companyId, Long branchId, Long syncBatchId,
                                                           Long afterErrorId, int pageSize) {
        String sql = """
                SELECT
                    e.id,
                    e.offline_order_import_id,
                    oi.offline_order_no,
                    e.error_stage,
                    e.error_code,
                    e.error_message,
                    e.field_path,
                    e.severity,
                    e.retry_allowed,
                    e.manager_review_required,
                    e.created_at
                FROM %s e
                JOIN %s oi ON oi.id = e.offline_order_import_id
                WHERE oi.sync_batch_id = ?
                  AND oi.company_id = ?
                  AND oi.branch_id = ?
                  AND e.company_id = ?
                  AND e.branch_id = ?
                  AND e.id > ?
                ORDER BY e.id ASC
                LIMIT ?
                """.formatted(
                TenantSqlIdentifiers.posOfflineOrderErrorTable(companyId),
                TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        return jdbcTemplate.query(sql, ERROR_ITEM_MAPPER,
                syncBatchId, companyId, branchId, companyId, branchId, afterErrorId, pageSize + 1);
    }

    public List<SyncErrorResponse> findErrorsByImportId(Long companyId, Long branchId, Long offlineOrderImportId) {
        String sql = """
                SELECT * FROM %s
                WHERE offline_order_import_id = ? AND company_id = ? AND branch_id = ?
                ORDER BY created_at ASC
                """.formatted(TenantSqlIdentifiers.posOfflineOrderErrorTable(companyId));
        return jdbcTemplate.query(sql, ERROR_RESPONSE_MAPPER, offlineOrderImportId, companyId, branchId);
    }
}
