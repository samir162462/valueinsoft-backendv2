package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.enums.PosClientType;
import com.example.valueinsoftbackend.pos.offline.enums.PosDeviceStatus;
import com.example.valueinsoftbackend.pos.offline.model.PosDeviceModel;
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
public class PosDeviceRepository {

    private final JdbcTemplate jdbcTemplate;

    public PosDeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // -------------------------------------------------------
    // RowMapper
    // -------------------------------------------------------

    private static final RowMapper<PosDeviceModel> ROW_MAPPER = (rs, rowNum) -> new PosDeviceModel(
            rs.getLong("id"),
            rs.getLong("company_id"),
            rs.getLong("branch_id"),
            rs.getString("device_code"),
            rs.getString("device_name"),
            PosClientType.valueOf(rs.getString("client_type")),
            rs.getString("platform"),
            PosDeviceStatus.valueOf(rs.getString("status")),
            rs.getBoolean("allowed_offline"),
            rs.getInt("max_offline_hours"),
            rs.getString("app_version"),
            rs.getTimestamp("last_heartbeat_at") != null
                    ? rs.getTimestamp("last_heartbeat_at").toInstant() : null,
            rs.getObject("registered_by") != null ? rs.getLong("registered_by") : null,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    // -------------------------------------------------------
    // Insert
    // -------------------------------------------------------

    public Long insertDevice(Long companyId, Long branchId, String deviceCode, String deviceName,
                             PosClientType clientType, String platform, String appVersion,
                             Long registeredBy) {
        String sql = """
                INSERT INTO %s (company_id, branch_id, device_code, device_name,
                    client_type, platform, app_version, registered_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.formatted(TenantSqlIdentifiers.posDeviceTable(companyId));
        return jdbcTemplate.queryForObject(sql, Long.class,
                companyId, branchId, deviceCode, deviceName,
                clientType.name(), platform, appVersion, registeredBy);
    }

    // -------------------------------------------------------
    // Lookups
    // -------------------------------------------------------

    public Optional<PosDeviceModel> findByCompanyBranchDeviceCode(Long companyId, Long branchId,
                                                                   String deviceCode) {
        String sql = """
                SELECT * FROM %s
                WHERE company_id = ? AND branch_id = ? AND device_code = ?
                """.formatted(TenantSqlIdentifiers.posDeviceTable(companyId));
        List<PosDeviceModel> results = jdbcTemplate.query(sql, ROW_MAPPER,
                companyId, branchId, deviceCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<PosDeviceModel> findById(Long companyId, Long branchId, Long id) {
        String sql = """
                SELECT * FROM %s
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posDeviceTable(companyId));
        List<PosDeviceModel> results = jdbcTemplate.query(sql, ROW_MAPPER, id, companyId, branchId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // -------------------------------------------------------
    // Updates
    // -------------------------------------------------------

    public void updateHeartbeat(Long companyId, Long branchId, Long id, String appVersion) {
        String sql = """
                UPDATE %s
                SET last_heartbeat_at = NOW(), app_version = COALESCE(?, app_version),
                    updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posDeviceTable(companyId));
        jdbcTemplate.update(sql, appVersion, id, companyId, branchId);
    }

    public void updateStatus(Long companyId, Long branchId, Long id, PosDeviceStatus status) {
        String sql = """
                UPDATE %s
                SET status = ?, updated_at = NOW()
                WHERE id = ? AND company_id = ? AND branch_id = ?
                """.formatted(TenantSqlIdentifiers.posDeviceTable(companyId));
        jdbcTemplate.update(sql, status.name(), id, companyId, branchId);
    }
}
