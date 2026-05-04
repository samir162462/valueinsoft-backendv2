package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class PosSyncBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public PosSyncBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------
    // RowMapper
    // -------------------------------------------------------

    private static final RowMapper<PosSyncBatchModel> ROW_MAPPER = (rs, rowNum) -> new PosSyncBatchModel(
            rs.getLong("id"),
            rs.getLong("company_id"),
            rs.getLong("branch_id"),
            rs.getLong("device_id"),
            rs.getLong("cashier_id"),
            rs.getString("client_batch_id"),
            rs.getString("client_type"),
            rs.getString("platform"),
            rs.getString("app_version"),
            PosSyncBatchStatus.valueOf(rs.getString("status")),
            rs.getInt("total_orders"),
            rs.getInt("synced_orders"),
            rs.getInt("failed_orders"),
            rs.getInt("duplicate_orders"),
            rs.getInt("needs_review_orders"),
            rs.getTimestamp("offline_started_at") != null
                    ? rs.getTimestamp("offline_started_at").toInstant() : null,
            rs.getTimestamp("sync_started_at") != null
                    ? rs.getTimestamp("sync_started_at").toInstant() : null,
            rs.getTimestamp("sync_completed_at") != null
                    ? rs.getTimestamp("sync_completed_at").toInstant() : null,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    // -------------------------------------------------------
    // Insert
    // -------------------------------------------------------

    public Long insertBatch(Long companyId, Long branchId, Long deviceId, Long cashierId,
                            String clientBatchId, String clientType, String platform,
                            String appVersion, int totalOrders,
                            Instant offlineStartedAt, Instant syncStartedAt) {
        String sql = """
                INSERT INTO %s (company_id, branch_id, device_id, cashier_id,
                    client_batch_id, client_type, platform, app_version,
                    total_orders, offline_started_at, sync_started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.formatted(TenantSqlIdentifiers.posSyncBatchTable(companyId));
        return jdbcTemplate.queryForObject(sql, Long.class,
                companyId, branchId, deviceId, cashierId,
                clientBatchId, clientType, platform, appVersion,
                totalOrders,
                offlineStartedAt != null ? java.sql.Timestamp.from(offlineStartedAt) : null,
                syncStartedAt != null ? java.sql.Timestamp.from(syncStartedAt) : null);
    }

    // -------------------------------------------------------
    // Lookups
    // -------------------------------------------------------

    public Optional<PosSyncBatchModel> findById(Long companyId, Long branchId, Long id) {
        String sql = """
                SELECT * FROM %s
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posSyncBatchTable(companyId));
        List<PosSyncBatchModel> results = jdbcTemplate.query(sql, ROW_MAPPER, id, companyId, branchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<PosSyncBatchModel> findActiveBatchesForWorker(Long companyId, Long branchId, int limit) {
        int safeLimit = Math.max(1, limit);
        String sql = """
                SELECT * FROM %s
                WHERE company_id = ?
                  AND branch_id = ?
                  AND status IN ('RECEIVED', 'IN_PROGRESS', 'PROCESSING', 'PARTIALLY_SYNCED')
                ORDER BY created_at ASC, id ASC
                LIMIT ?
                """.formatted(TenantSqlIdentifiers.posSyncBatchTable(companyId));
        return jdbcTemplate.query(sql, ROW_MAPPER, companyId, branchId, safeLimit);
    }

    // -------------------------------------------------------
    // Updates
    // -------------------------------------------------------

    public void updateStatus(Long companyId, Long branchId, Long id, PosSyncBatchStatus status) {
        String sql = """
                UPDATE %s
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posSyncBatchTable(companyId));
        jdbcTemplate.update(sql, status.name(), id, companyId, branchId);
    }

    public void updateSummary(Long companyId, Long branchId, Long id, PosSyncBatchStatus status,
                              int syncedOrders, int failedOrders,
                              int duplicateOrders, int needsReviewOrders) {
        String sql = """
                UPDATE %s
                SET status = ?, synced_orders = ?, failed_orders = ?,
                    duplicate_orders = ?, needs_review_orders = ?,
                    sync_completed_at = NOW(), updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posSyncBatchTable(companyId));
        jdbcTemplate.update(sql, status.name(), syncedOrders, failedOrders,
                duplicateOrders, needsReviewOrders, id, companyId, branchId);
    }

    public void recalculateSummary(Long companyId, Long branchId, Long id) {
        String batchTable = TenantSqlIdentifiers.posSyncBatchTable(companyId);
        String importTable = TenantSqlIdentifiers.posOfflineOrderImportTable(companyId);
        String sql = """
                WITH counts AS (
                    SELECT
                        COUNT(*)::int AS total_count,
                        COUNT(*) FILTER (WHERE status = 'PENDING')::int AS pending_count,
                        COUNT(*) FILTER (WHERE status = 'PENDING_RETRY')::int AS pending_retry_count,
                        COUNT(*) FILTER (WHERE status = 'PROCESSING')::int AS processing_count,
                        COUNT(*) FILTER (WHERE status = 'READY_FOR_VALIDATION')::int AS ready_for_validation_count,
                        COUNT(*) FILTER (WHERE status = 'VALIDATING')::int AS validating_count,
                        COUNT(*) FILTER (WHERE status = 'VALIDATED')::int AS validated_count,
                        COUNT(*) FILTER (WHERE status = 'POSTING')::int AS posting_count,
                        COUNT(*) FILTER (WHERE status = 'SYNCED')::int AS synced_count,
                        COUNT(*) FILTER (WHERE status = 'POSTING_FAILED')::int AS posting_failed_count,
                        COUNT(*) FILTER (WHERE status = 'VALIDATION_FAILED')::int AS validation_failed_count,
                        COUNT(*) FILTER (WHERE status = 'FAILED')::int AS failed_count,
                        COUNT(*) FILTER (WHERE status = 'DUPLICATE')::int AS duplicate_count,
                        COUNT(*) FILTER (WHERE status = 'NEEDS_REVIEW')::int AS needs_review_count
                    FROM %s
                    WHERE company_id = ? AND branch_id = ? AND sync_batch_id = ?
                ), summarized AS (
                    SELECT *,
                        (pending_count + pending_retry_count + processing_count + ready_for_validation_count
                         + validating_count + validated_count + posting_count) AS active_count,
                        (posting_failed_count + validation_failed_count + failed_count
                         + duplicate_count + needs_review_count) AS issue_count
                    FROM counts
                )
                UPDATE %s b
                SET total_orders = summarized.total_count,
                    synced_orders = summarized.synced_count,
                    failed_orders = summarized.failed_count + summarized.posting_failed_count + summarized.validation_failed_count,
                    duplicate_orders = summarized.duplicate_count,
                    needs_review_orders = summarized.needs_review_count,
                    pending_orders = summarized.pending_count,
                    pending_retry_orders = summarized.pending_retry_count,
                    processing_orders = summarized.processing_count,
                    ready_for_validation_orders = summarized.ready_for_validation_count,
                    validating_orders = summarized.validating_count,
                    validated_orders = summarized.validated_count,
                    posting_orders = summarized.posting_count,
                    posting_failed_orders = summarized.posting_failed_count,
                    validation_failed_orders = summarized.validation_failed_count,
                    status = CASE
                        WHEN summarized.active_count > 0 THEN 'IN_PROGRESS'
                        WHEN summarized.total_count = 0 THEN 'RECEIVED'
                        WHEN summarized.synced_count = summarized.total_count THEN 'COMPLETED'
                        WHEN summarized.synced_count = 0 AND summarized.issue_count > 0 THEN 'FAILED'
                        ELSE 'COMPLETED_WITH_ERRORS'
                    END,
                    sync_completed_at = CASE
                        WHEN summarized.active_count = 0 AND summarized.total_count > 0 THEN COALESCE(b.sync_completed_at, NOW())
                        ELSE NULL
                    END,
                    updated_at = NOW()
                FROM summarized
                WHERE b.id = ? AND b.company_id = ? AND b.branch_id = ?
                """.formatted(importTable, batchTable);
        jdbcTemplate.update(sql, companyId, branchId, id, id, companyId, branchId);
    }
}
