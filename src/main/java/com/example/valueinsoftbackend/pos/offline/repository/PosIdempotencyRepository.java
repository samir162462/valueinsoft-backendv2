package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.enums.PosIdempotencyStatus;
import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class PosIdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public PosIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------
    // RowMapper
    // -------------------------------------------------------

    private static final RowMapper<PosIdempotencyModel> ROW_MAPPER = (rs, rowNum) -> new PosIdempotencyModel(
            rs.getLong("id"),
            rs.getLong("company_id"),
            rs.getLong("branch_id"),
            rs.getLong("device_id"),
            rs.getString("idempotency_key"),
            rs.getString("offline_order_no"),
            rs.getString("request_hash"),
            PosIdempotencyStatus.valueOf(rs.getString("status")),
            rs.getObject("official_order_id") != null ? rs.getLong("official_order_id") : null,
            rs.getString("official_invoice_no"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    // -------------------------------------------------------
    // Existence check
    // -------------------------------------------------------

    public boolean existsByKey(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        String sql = """
                SELECT COUNT(*) FROM %s
                WHERE company_id = ? AND branch_id = ? AND device_id = ? AND idempotency_key = ?
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                companyId, branchId, deviceId, idempotencyKey);
        return count != null && count > 0;
    }

    // -------------------------------------------------------
    // Insert
    // -------------------------------------------------------

    public Long insertProcessingKey(Long companyId, Long branchId, Long deviceId,
                                    String idempotencyKey, String offlineOrderNo,
                                    String requestHash) {
        String sql = """
                INSERT INTO %s
                    (company_id, branch_id, device_id, idempotency_key,
                     offline_order_no, request_hash)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        return jdbcTemplate.queryForObject(sql, Long.class,
                companyId, branchId, deviceId,
                idempotencyKey, offlineOrderNo, requestHash);
    }

    public Long insertReceivedKey(Long companyId, Long branchId, Long deviceId,
                                  String idempotencyKey, String offlineOrderNo,
                                  String requestHash) {
        String sql = """
                INSERT INTO %s
                    (company_id, branch_id, device_id, idempotency_key,
                     offline_order_no, request_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED')
                RETURNING id
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        return jdbcTemplate.queryForObject(sql, Long.class,
                companyId, branchId, deviceId,
                idempotencyKey, offlineOrderNo, requestHash);
    }

    // -------------------------------------------------------
    // Status Updates
    // -------------------------------------------------------

    public void markSynced(Long companyId, Long branchId, Long deviceId,
                           String idempotencyKey, Long officialOrderId,
                           String officialInvoiceNo) {
        String sql = """
                UPDATE %s
                SET status = 'SYNCED', official_order_id = ?, official_invoice_no = ?,
                    updated_at = NOW()
                WHERE company_id = ? AND branch_id = ? AND device_id = ? AND idempotency_key = ?
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        jdbcTemplate.update(sql, officialOrderId, officialInvoiceNo,
                companyId, branchId, deviceId, idempotencyKey);
    }

    public void markFailed(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        String sql = """
                UPDATE %s
                SET status = 'FAILED', updated_at = NOW()
                WHERE company_id = ? AND branch_id = ? AND device_id = ? AND idempotency_key = ?
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        jdbcTemplate.update(sql, companyId, branchId, deviceId, idempotencyKey);
    }

    public void markPayloadMismatch(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        String sql = """
                UPDATE %s
                SET status = 'PAYLOAD_MISMATCH', updated_at = NOW()
                WHERE company_id = ? AND branch_id = ? AND device_id = ? AND idempotency_key = ?
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        jdbcTemplate.update(sql, companyId, branchId, deviceId, idempotencyKey);
    }

    // -------------------------------------------------------
    // Lookup
    // -------------------------------------------------------

    public Optional<PosIdempotencyModel> findByKey(Long companyId, Long branchId,
                                                    Long deviceId, String idempotencyKey) {
        String sql = """
                SELECT * FROM %s
                WHERE company_id = ? AND branch_id = ? AND device_id = ? AND idempotency_key = ?
                """.formatted(TenantSqlIdentifiers.posIdempotencyKeyTable(companyId));
        List<PosIdempotencyModel> results = jdbcTemplate.query(sql, ROW_MAPPER,
                companyId, branchId, deviceId, idempotencyKey);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
