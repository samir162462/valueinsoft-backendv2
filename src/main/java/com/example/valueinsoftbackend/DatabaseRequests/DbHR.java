package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.HR.*;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class DbHR {

    private final JdbcTemplate jdbcTemplate;

    public DbHR(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Row Mappers
    private final RowMapper<Employee> employeeMapper = (rs, rowNum) -> new Employee(
            rs.getInt("id"),
            rs.getInt("company_id"),
            rs.getInt("branch_id"),
            rs.getString("employee_code"),
            rs.getString("pin_hash"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            (Integer) rs.getObject("user_id"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at"),
            rs.getString("created_by"),
            rs.getTimestamp("updated_at"),
            rs.getString("updated_by")
    );

    private final RowMapper<Shift> shiftMapper = (rs, rowNum) -> new Shift(
            rs.getInt("id"),
            rs.getInt("company_id"),
            rs.getInt("branch_id"),
            rs.getString("shift_name"),
            rs.getTime("start_time"),
            rs.getTime("end_time"),
            rs.getInt("grace_minutes"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at"),
            rs.getString("created_by"),
            rs.getTimestamp("updated_at"),
            rs.getString("updated_by")
    );

    private final RowMapper<AttendanceDay> attendanceDayMapper = (rs, rowNum) -> new AttendanceDay(
            rs.getLong("id"),
            rs.getInt("company_id"),
            rs.getInt("branch_id"),
            rs.getInt("employee_id"),
            rs.getDate("attendance_date"),
            rs.getTimestamp("clock_in"),
            rs.getTimestamp("clock_out"),
            rs.getInt("working_minutes"),
            rs.getInt("break_minutes"),
            rs.getInt("late_minutes"),
            rs.getInt("overtime_minutes"),
            rs.getString("status"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at")
    );

    // Employee CRUD
    public List<Employee> getAllEmployees(int companyId, int branchId) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrEmployeeTable(companyId) + " WHERE branch_id = ?";
        return jdbcTemplate.query(sql, employeeMapper, branchId);
    }

    public Employee getEmployeeByCode(int companyId, int branchId, String code) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrEmployeeTable(companyId) + " WHERE branch_id = ? AND employee_code = ?";
        List<Employee> results = jdbcTemplate.query(sql, employeeMapper, branchId, code);
        return results.isEmpty() ? null : results.get(0);
    }

    public boolean isUserEmployee(int companyId, int userId) {
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.hrEmployeeTable(companyId) + " WHERE user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }

    public Employee getEmployeeByUser(int companyId, int userId) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrEmployeeTable(companyId) + " WHERE user_id = ?";
        List<Employee> results = jdbcTemplate.query(sql, employeeMapper, userId);
        return results.isEmpty() ? null : results.get(0);
    }

    public void updateEmployee(Employee employee) {
        String sql = "UPDATE " + TenantSqlIdentifiers.hrEmployeeTable(employee.getCompanyId()) + 
                " SET employee_code = ?, is_active = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        jdbcTemplate.update(sql, employee.getEmployeeCode(), employee.isActive(), employee.getUpdatedBy(), employee.getId());
    }

    public int addEmployee(Employee employee) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.hrEmployeeTable(employee.getCompanyId()) + 
                " (company_id, branch_id, employee_code, pin_hash, first_name, last_name, user_id, is_active, created_by, updated_by) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, employee.getCompanyId());
            ps.setInt(2, employee.getBranchId());
            ps.setString(3, employee.getEmployeeCode());
            ps.setString(4, employee.getPinHash());
            ps.setString(5, employee.getFirstName());
            ps.setString(6, employee.getLastName());
            ps.setObject(7, employee.getUserId());
            ps.setBoolean(8, employee.isActive());
            ps.setString(9, employee.getCreatedBy());
            ps.setString(10, employee.getUpdatedBy());
            return ps;
        }, keyHolder);
        return (int) keyHolder.getKeys().get("id");
    }

    // Shift CRUD
    public List<Shift> getAllShifts(int companyId, int branchId) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrShiftTable(companyId) + " WHERE branch_id = ?";
        return jdbcTemplate.query(sql, shiftMapper, branchId);
    }

    public int addShift(Shift shift) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.hrShiftTable(shift.getCompanyId()) + 
                " (company_id, branch_id, shift_name, start_time, end_time, grace_minutes, is_active, created_by, updated_by) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, shift.getCompanyId());
            ps.setInt(2, shift.getBranchId());
            ps.setString(3, shift.getShiftName());
            ps.setTime(4, shift.getStartTime());
            ps.setTime(5, shift.getEndTime());
            ps.setInt(6, shift.getGraceMinutes());
            ps.setBoolean(7, shift.isActive());
            ps.setString(8, shift.getCreatedBy());
            ps.setString(9, shift.getUpdatedBy());
            return ps;
        }, keyHolder);
        return (int) keyHolder.getKeys().get("id");
    }

    // Assignment
    public void assignShift(EmployeeShift assignment) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.hrEmployeeShiftTable(assignment.getCompanyId()) + 
                " (company_id, branch_id, employee_id, shift_id, effective_from, effective_to, created_by, updated_by) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, 
                assignment.getCompanyId(), 
                assignment.getBranchId(), 
                assignment.getEmployeeId(), 
                assignment.getShiftId(), 
                assignment.getEffectiveFrom(), 
                assignment.getEffectiveTo(), 
                assignment.getCreatedBy(), 
                assignment.getUpdatedBy());
    }

    public List<EmployeeShift> getAllAssignments(int companyId, int branchId) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrEmployeeShiftTable(companyId) + " WHERE branch_id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new EmployeeShift(
                rs.getInt("id"),
                rs.getInt("company_id"),
                rs.getInt("branch_id"),
                rs.getInt("employee_id"),
                rs.getInt("shift_id"),
                rs.getDate("effective_from"),
                rs.getDate("effective_to"),
                rs.getTimestamp("created_at"),
                rs.getString("created_by"),
                rs.getTimestamp("updated_at"),
                rs.getString("updated_by")
        ), branchId);
    }

    public Shift getActiveShiftForEmployee(int companyId, int employeeId, java.sql.Date date) {
        String sql = "SELECT s.* FROM " + TenantSqlIdentifiers.hrShiftTable(companyId) + " s " +
                " JOIN " + TenantSqlIdentifiers.hrEmployeeShiftTable(companyId) + " es ON s.id = es.shift_id " +
                " WHERE es.employee_id = ? AND es.effective_from <= ? AND (es.effective_to IS NULL OR es.effective_to >= ?)";
        List<Shift> results = jdbcTemplate.query(sql, shiftMapper, employeeId, date, date);
        return results.isEmpty() ? null : results.get(0);
    }

    // Attendance
    public void saveLog(AttendanceLog log) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.hrAttendanceLogTable(log.getCompanyId()) + 
                " (company_id, branch_id, employee_id, action_type, action_time, source, device_id, ip_address, user_agent, remarks, correction_reason, manager_id, created_by) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, 
                log.getCompanyId(), 
                log.getBranchId(), 
                log.getEmployeeId(), 
                log.getActionType(), 
                log.getActionTime(), 
                log.getSource(), 
                log.getDeviceId(), 
                log.getIpAddress(), 
                log.getUserAgent(), 
                log.getRemarks(), 
                log.getCorrectionReason(), 
                log.getManagerId(), 
                log.getCreatedBy());
    }

    public AttendanceDay getAttendanceDay(int companyId, int employeeId, java.sql.Date date) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrAttendanceDayTable(companyId) + " WHERE employee_id = ? AND attendance_date = ?";
        List<AttendanceDay> results = jdbcTemplate.query(sql, attendanceDayMapper, employeeId, date);
        return results.isEmpty() ? null : results.get(0);
    }

    public void upsertAttendanceDay(AttendanceDay day) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.hrAttendanceDayTable(day.getCompanyId()) + 
                " (company_id, branch_id, employee_id, attendance_date, clock_in, clock_out, working_minutes, break_minutes, late_minutes, overtime_minutes, status) " +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                " ON CONFLICT (company_id, branch_id, employee_id, attendance_date) " +
                " DO UPDATE SET clock_in = EXCLUDED.clock_in, clock_out = EXCLUDED.clock_out, " +
                " working_minutes = EXCLUDED.working_minutes, break_minutes = EXCLUDED.break_minutes, " +
                " late_minutes = EXCLUDED.late_minutes, overtime_minutes = EXCLUDED.overtime_minutes, " +
                " status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql, 
                day.getCompanyId(), 
                day.getBranchId(), 
                day.getEmployeeId(), 
                day.getAttendanceDate(), 
                day.getClockIn(), 
                day.getClockOut(), 
                day.getWorkingMinutes(), 
                day.getBreakMinutes(), 
                day.getLateMinutes(), 
                day.getOvertimeMinutes(), 
                day.getStatus());
    }

    public List<AttendanceDay> getAttendanceReport(int companyId, int branchId, java.sql.Date start, java.sql.Date end) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrAttendanceDayTable(companyId) + 
                " WHERE branch_id = ? AND attendance_date BETWEEN ? AND ?";
        return jdbcTemplate.query(sql, attendanceDayMapper, branchId, start, end);
    }

    public List<AttendanceLog> getAttendanceLogs(int companyId, int employeeId, java.sql.Date date) {
        String sql = "SELECT * FROM " + TenantSqlIdentifiers.hrAttendanceLogTable(companyId) + 
                " WHERE employee_id = ? AND action_time::date = ? ORDER BY action_time ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AttendanceLog log = new AttendanceLog();
            log.setId(rs.getLong("id"));
            log.setActionType(rs.getString("action_type"));
            log.setActionTime(rs.getTimestamp("action_time"));
            return log;
        }, employeeId, date);
    }
}
