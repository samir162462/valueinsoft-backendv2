package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class OfflineOrderImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public OfflineOrderImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------
    // RowMapper
    // -------------------------------------------------------

    private static final RowMapper<OfflineOrderImportModel> ROW_MAPPER = (rs, rowNum) -> new OfflineOrderImportModel(
            rs.getLong("id"),
            rs.getLong("sync_batch_id"),
            rs.getLong("company_id"),
            rs.getLong("branch_id"),
            rs.getLong("device_id"),
            rs.getLong("cashier_id"),
            rs.getString("offline_order_no"),
            rs.getString("idempotency_key"),
            rs.getTimestamp("local_order_created_at") != null
                    ? rs.getTimestamp("local_order_created_at").toInstant() : null,
            rs.getString("payload_json"),
            rs.getString("payload_hash"),
            OfflineOrderImportStatus.valueOf(rs.getString("status")),
            rs.getObject("official_order_id") != null ? rs.getLong("official_order_id") : null,
            rs.getString("official_invoice_no"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getInt("retry_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("processing_started_at") != null
                    ? rs.getTimestamp("processing_started_at").toInstant() : null,
            rs.getTimestamp("processed_at") != null
                    ? rs.getTimestamp("processed_at").toInstant() : null,
            rs.getTimestamp("updated_at").toInstant()
    );

    // -------------------------------------------------------
    // Insert
    // -------------------------------------------------------

    public Long insertImport(Long syncBatchId, Long companyId, Long branchId,
                             Long deviceId, Long cashierId,
                             String offlineOrderNo, String idempotencyKey,
                             Instant localOrderCreatedAt,
                             String payloadJson, String payloadHash) {
        String sql = """
                INSERT INTO %s
                    (sync_batch_id, company_id, branch_id, device_id, cashier_id,
                     offline_order_no, idempotency_key, local_order_created_at,
                     payload_json, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                RETURNING id
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        return jdbcTemplate.queryForObject(sql, Long.class,
                syncBatchId, companyId, branchId, deviceId, cashierId,
                offlineOrderNo, idempotencyKey,
                localOrderCreatedAt != null ? Timestamp.from(localOrderCreatedAt) : null,
                payloadJson, payloadHash);
    }

    // -------------------------------------------------------
    // Lookups
    // -------------------------------------------------------

    public List<OfflineOrderImportModel> findByBatchId(Long companyId, Long branchId, Long syncBatchId) {
        String sql = """
                SELECT * FROM %s
                WHERE sync_batch_id = ? AND company_id = ? AND branch_id = ?
                ORDER BY id ASC
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        return jdbcTemplate.query(sql, ROW_MAPPER, syncBatchId, companyId, branchId);
    }

    public Optional<OfflineOrderImportModel> findByImportId(Long companyId, Long branchId, Long id) {
        String sql = """
                SELECT * FROM %s
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER, id, companyId, branchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<OfflineOrderImportModel> findByIdempotencyKey(Long companyId, Long branchId,
                                                                  Long deviceId, String idempotencyKey) {
        String sql = """
                SELECT * FROM %s
                WHERE company_id = ? AND branch_id = ? AND device_id = ? AND idempotency_key = ?
                ORDER BY id ASC
                LIMIT 1
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER,
                companyId, branchId, deviceId, idempotencyKey);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<OfflineOrderImportModel> claimNextPendingImport(Long companyId, Long branchId, Long syncBatchId) {
        String table = TenantSqlIdentifiers.posOfflineOrderImportTable(companyId);
        String sql = """
                UPDATE %s
                SET status = 'PROCESSING',
                    processing_started_at = NOW(),
                    updated_at = NOW()
                WHERE id = (
                    SELECT id
                    FROM %s
                    WHERE company_id = ?
                      AND branch_id = ?
                      AND sync_batch_id = ?
                      AND status IN ('PENDING', 'PENDING_RETRY')
                    ORDER BY id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING *
                """.formatted(table, table);
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER, companyId, branchId, syncBatchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<OfflineOrderImportModel> claimImportForProcessing(Long companyId, Long branchId, Long id) {
        String sql = """
                UPDATE %s
                SET status = 'PROCESSING',
                    processing_started_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND company_id = ?
                  AND branch_id = ?
                  AND status IN ('PENDING', 'PENDING_RETRY')
                RETURNING *
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER, id, companyId, branchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<OfflineOrderImportModel> claimNextReadyForValidation(Long companyId, Long branchId, Long syncBatchId) {
        String table = TenantSqlIdentifiers.posOfflineOrderImportTable(companyId);
        String sql = """
                UPDATE %s
                SET status = 'VALIDATING',
                    updated_at = NOW()
                WHERE id = (
                    SELECT id
                    FROM %s
                    WHERE company_id = ?
                      AND branch_id = ?
                      AND sync_batch_id = ?
                      AND status = 'READY_FOR_VALIDATION'
                    ORDER BY id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING *
                """.formatted(table, table);
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER, companyId, branchId, syncBatchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<OfflineOrderImportModel> claimImportForValidation(Long companyId, Long branchId, Long id) {
        String sql = """
                UPDATE %s
                SET status = 'VALIDATING',
                    updated_at = NOW()
                WHERE id = ?
                  AND company_id = ?
                  AND branch_id = ?
                  AND status = 'READY_FOR_VALIDATION'
                RETURNING *
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER, id, companyId, branchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // -------------------------------------------------------
    // Status Updates
    // -------------------------------------------------------

    public void updateStatus(Long companyId, Long branchId, Long id, OfflineOrderImportStatus status) {
        String sql = """
                UPDATE %s
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, status.name(), id, companyId, branchId);
    }

    public void markProcessed(Long companyId, Long branchId, Long id, OfflineOrderImportStatus status,
                              Long officialOrderId, String officialInvoiceNo) {
        String sql = """
                UPDATE %s
                SET status = ?, official_order_id = ?, official_invoice_no = ?,
                    processed_at = NOW(), updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, status.name(), officialOrderId, officialInvoiceNo, id, companyId, branchId);
    }

    public void markReadyForValidation(Long companyId, Long branchId, Long id) {
        String sql = """
                UPDATE %s
                SET status = 'READY_FOR_VALIDATION',
                    processed_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ? AND status = 'PROCESSING'
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, id, companyId, branchId);
    }

    public void markProcessingFailed(Long companyId, Long branchId, Long id, String errorCode, String errorMessage) {
        String sql = """
                UPDATE %s
                SET status = 'FAILED',
                    error_code = ?,
                    error_message = ?,
                    processed_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, errorCode, errorMessage, id, companyId, branchId);
    }

    public void markValidated(Long companyId, Long branchId, Long id) {
        String sql = """
                UPDATE %s
                SET status = 'VALIDATED',
                    error_code = NULL,
                    error_message = NULL,
                    processed_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ? AND status = 'VALIDATING'
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, id, companyId, branchId);
    }

    public void markValidationFailed(Long companyId, Long branchId, Long id, String errorCode, String errorMessage) {
        String sql = """
                UPDATE %s
                SET status = 'VALIDATION_FAILED',
                    error_code = ?,
                    error_message = ?,
                    processed_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, errorCode, errorMessage, id, companyId, branchId);
    }

    public void markFailed(Long companyId, Long branchId, Long id, String errorCode, String errorMessage) {
        String sql = """
                UPDATE %s
                SET status = 'FAILED', error_code = ?, error_message = ?,
                    retry_count = retry_count + 1, updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        jdbcTemplate.update(sql, errorCode, errorMessage, id, companyId, branchId);
    }

    public int markPendingRetry(Long companyId, Long branchId, Long id) {
        String sql = """
                UPDATE %s
                SET status = 'PENDING_RETRY',
                    error_code = NULL,
                    error_message = NULL,
                    retry_count = retry_count + 1,
                    last_retry_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND company_id = ?
                  AND branch_id = ?
                  AND status IN ('FAILED', 'NEEDS_REVIEW')
                """.formatted(TenantSqlIdentifiers.posOfflineOrderImportTable(companyId));
        return jdbcTemplate.update(sql, id, companyId, branchId);
    }
}
