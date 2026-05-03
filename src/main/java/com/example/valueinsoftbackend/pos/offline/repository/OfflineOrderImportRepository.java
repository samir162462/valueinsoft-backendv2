package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
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
                INSERT INTO pos_offline_order_import
                    (sync_batch_id, company_id, branch_id, device_id, cashier_id,
                     offline_order_no, idempotency_key, local_order_created_at,
                     payload_json, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, Long.class,
                syncBatchId, companyId, branchId, deviceId, cashierId,
                offlineOrderNo, idempotencyKey,
                localOrderCreatedAt != null ? Timestamp.from(localOrderCreatedAt) : null,
                payloadJson, payloadHash);
    }

    // -------------------------------------------------------
    // Lookups
    // -------------------------------------------------------

    public List<OfflineOrderImportModel> findByBatchId(Long syncBatchId) {
        String sql = "SELECT * FROM pos_offline_order_import WHERE sync_batch_id = ? ORDER BY id ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, syncBatchId);
    }

    public Optional<OfflineOrderImportModel> findById(Long id) {
        String sql = "SELECT * FROM pos_offline_order_import WHERE id = ?";
        List<OfflineOrderImportModel> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // -------------------------------------------------------
    // Status Updates
    // -------------------------------------------------------

    public void updateStatus(Long id, OfflineOrderImportStatus status) {
        String sql = """
                UPDATE pos_offline_order_import
                SET status = ?, updated_at = NOW()
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, status.name(), id);
    }

    public void markProcessed(Long id, OfflineOrderImportStatus status,
                              Long officialOrderId, String officialInvoiceNo) {
        String sql = """
                UPDATE pos_offline_order_import
                SET status = ?, official_order_id = ?, official_invoice_no = ?,
                    processed_at = NOW(), updated_at = NOW()
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, status.name(), officialOrderId, officialInvoiceNo, id);
    }

    public void markFailed(Long id, String errorCode, String errorMessage) {
        String sql = """
                UPDATE pos_offline_order_import
                SET status = 'FAILED', error_code = ?, error_message = ?,
                    retry_count = retry_count + 1, updated_at = NOW()
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, errorCode, errorMessage, id);
    }
}
