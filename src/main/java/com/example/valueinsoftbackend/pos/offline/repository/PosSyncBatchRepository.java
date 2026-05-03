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
}
