package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorResponse;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
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

    // -------------------------------------------------------
    // Insert
    // -------------------------------------------------------

    public Long insertError(Long offlineOrderImportId, Long companyId, Long branchId,
                            String errorStage, String errorCode, String errorMessage,
                            String fieldPath, String rawValue,
                            OfflineErrorSeverity severity,
                            boolean retryAllowed, boolean managerReviewRequired) {
        String sql = """
                INSERT INTO pos_offline_order_error
                    (offline_order_import_id, company_id, branch_id,
                     error_stage, error_code, error_message,
                     field_path, raw_value, severity,
                     retry_allowed, manager_review_required)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, Long.class,
                offlineOrderImportId, companyId, branchId,
                errorStage, errorCode, errorMessage,
                fieldPath, rawValue, severity.name(),
                retryAllowed, managerReviewRequired);
    }

    // -------------------------------------------------------
    // Lookups
    // -------------------------------------------------------

    public List<SyncErrorResponse> findErrorsByBatchId(Long syncBatchId) {
        String sql = """
                SELECT e.* FROM pos_offline_order_error e
                JOIN pos_offline_order_import oi ON oi.id = e.offline_order_import_id
                WHERE oi.sync_batch_id = ?
                ORDER BY e.created_at ASC
                """;
        return jdbcTemplate.query(sql, ERROR_RESPONSE_MAPPER, syncBatchId);
    }

    public List<SyncErrorResponse> findErrorsByImportId(Long offlineOrderImportId) {
        String sql = """
                SELECT * FROM pos_offline_order_error
                WHERE offline_order_import_id = ?
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, ERROR_RESPONSE_MAPPER, offlineOrderImportId);
    }
}
