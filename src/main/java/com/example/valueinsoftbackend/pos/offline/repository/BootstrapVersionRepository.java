package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.model.BootstrapVersionModel;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class BootstrapVersionRepository {

    private final JdbcTemplate jdbcTemplate;

    public BootstrapVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<BootstrapVersionModel> ROW_MAPPER = (rs, rowNum) -> new BootstrapVersionModel(
            rs.getLong("id"),
            rs.getLong("company_id"),
            rs.getLong("branch_id"),
            rs.getString("data_type"),
            rs.getLong("version_no"),
            rs.getString("checksum"),
            rs.getTimestamp("last_changed_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    public Optional<BootstrapVersionModel> findVersion(Long companyId, Long branchId, String dataType) {
        String sql = """
                SELECT * FROM %s
                WHERE company_id = ? AND branch_id = ? AND data_type = ?
                """.formatted(TenantSqlIdentifiers.posBootstrapVersionTable(companyId));
        List<BootstrapVersionModel> results = jdbcTemplate.query(sql, ROW_MAPPER, companyId, branchId, dataType);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void upsertVersion(Long companyId, Long branchId, String dataType, long versionNo, String checksum) {
        String sql = """
                INSERT INTO %s (company_id, branch_id, data_type, version_no, checksum)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (company_id, branch_id, data_type)
                DO UPDATE SET version_no = EXCLUDED.version_no, checksum = EXCLUDED.checksum,
                              last_changed_at = NOW(), updated_at = NOW()
                """.formatted(TenantSqlIdentifiers.posBootstrapVersionTable(companyId));
        jdbcTemplate.update(sql, companyId, branchId, dataType, versionNo, checksum);
    }
}
