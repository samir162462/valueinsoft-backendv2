package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.ShiftPeriod;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DbPosShiftPeriod {

    private final JdbcTemplate jdbcTemplate;

    public DbPosShiftPeriod(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasOpenShift(int companyId, int branchId) {
        String sql = "SELECT EXISTS (" +
                "SELECT 1 FROM " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? AND \"ShiftEndTime\" IS NULL)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, branchId);
        return exists != null && exists;
    }

    public ShiftPeriod insertShift(int companyId, int branchId, Timestamp startTime) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " (\"ShiftStartTime\", \"ShiftEndTime\", \"branchId\") VALUES (?, NULL, ?) " +
                "RETURNING \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\"";

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ShiftPeriod(
                rs.getInt("PosSOID"),
                rs.getTimestamp("ShiftStartTime"),
                rs.getTimestamp("ShiftEndTime"),
                null
        ), startTime, branchId);
    }

    public int closeShift(int companyId, int shiftPeriodId, Timestamp endTime) {
        String sql = "UPDATE " + TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " SET \"ShiftEndTime\" = ? WHERE \"PosSOID\" = ?";
        return jdbcTemplate.update(sql, endTime, shiftPeriodId);
    }

    public ShiftPeriod getCurrentShift(int companyId, int branchId) {
        String sql = "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\" FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? AND \"ShiftEndTime\" IS NULL";

        List<ShiftPeriod> shifts = jdbcTemplate.query(sql, (rs, rowNum) -> new ShiftPeriod(
                rs.getInt("PosSOID"),
                rs.getTimestamp("ShiftStartTime"),
                rs.getTimestamp("ShiftEndTime"),
                null
        ), branchId);

        return shifts.isEmpty() ? null : shifts.get(0);
    }

    public ArrayList<ShiftPeriod> getBranchShifts(int companyId, int branchId) {
        String sql = "SELECT \"PosSOID\", \"ShiftStartTime\", \"ShiftEndTime\" FROM " +
                TenantSqlIdentifiers.shiftPeriodTable(companyId) +
                " WHERE \"branchId\" = ? ORDER BY \"PosSOID\" DESC";

        return new ArrayList<>(jdbcTemplate.query(sql, (rs, rowNum) -> new ShiftPeriod(
                rs.getInt("PosSOID"),
                rs.getTimestamp("ShiftStartTime"),
                rs.getTimestamp("ShiftEndTime"),
                null
        ), branchId));
    }
}
