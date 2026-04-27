package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.HR.*;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class HRService {

    private final DbHR dbHR;
    private final DbUsers dbUsers;
    private final PasswordEncoder passwordEncoder;

    public HRService(DbHR dbHR, DbUsers dbUsers, PasswordEncoder passwordEncoder) {
        this.dbHR = dbHR;
        this.dbUsers = dbUsers;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public int syncFromUsers(int companyId, int branchId, String creator) {
        List<User> users = dbUsers.getAllUsers(branchId);
        int count = 0;
        for (User user : users) {
            Employee existing = dbHR.getEmployeeByUser(companyId, user.getUserId());
            if (existing == null) {
                Employee emp = new Employee();
                emp.setCompanyId(companyId);
                emp.setBranchId(branchId);
                emp.setFirstName(user.getFirstName());
                emp.setLastName(user.getLastName());
                emp.setUserId(user.getUserId());
                
                // Generate numeric code: just the User ID
                String code = String.valueOf(user.getUserId());
                emp.setEmployeeCode(code);
                emp.setActive(true);
                emp.setCreatedBy(creator);
                emp.setUpdatedBy(creator);
                
                // Default PIN 1234
                createEmployee(emp, "1234");
                count++;
            } else {
                // Update existing employee code to be numeric if it's not
                String numericCode = String.valueOf(user.getUserId());
                if (!numericCode.equals(existing.getEmployeeCode())) {
                    existing.setEmployeeCode(numericCode);
                    existing.setUpdatedBy(creator);
                    dbHR.updateEmployee(existing);
                    count++;
                }
            }
        }
        return count;
    }

    public Employee getEmployeeByUsername(int companyId, String username) {
        User user = dbUsers.getUser(username);
        if (user == null) return null;
        return dbHR.getEmployeeByUser(companyId, user.getUserId());
    }

    public Employee createEmployee(Employee employee, String plainPin) {
        employee.setPinHash(passwordEncoder.encode(plainPin));
        int id = dbHR.addEmployee(employee);
        employee.setId(id);
        return employee;
    }

    @Transactional
    public void processAttendanceAction(int companyId, int branchId, String code, String pin, String actionType, String source, String deviceId, String ipAddress, String userAgent) {
        Employee employee = dbHR.getEmployeeByCode(companyId, branchId, code);
        if (employee == null || !passwordEncoder.matches(pin, employee.getPinHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid employee code or PIN");
        }

        AttendanceLog log = new AttendanceLog();
        log.setCompanyId(companyId);
        log.setBranchId(branchId);
        log.setEmployeeId(employee.getId());
        log.setActionType(actionType);
        log.setActionTime(new Timestamp(System.currentTimeMillis()));
        log.setSource(source);
        log.setDeviceId(deviceId);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedBy("kiosk");

        dbHR.saveLog(log);
        recalculateDailySummary(companyId, branchId, employee.getId(), LocalDate.now());
    }

    @Transactional
    public void manualCorrection(int companyId, int branchId, int employeeId, LocalDate date, String actionType, Timestamp time, String reason, String managerId) {
        AttendanceLog log = new AttendanceLog();
        log.setCompanyId(companyId);
        log.setBranchId(branchId);
        log.setEmployeeId(employeeId);
        log.setActionType("MANUAL_CORRECTION");
        log.setActionTime(new Timestamp(System.currentTimeMillis()));
        log.setRemarks("Correction for " + actionType + " at " + time);
        log.setCorrectionReason(reason);
        log.setManagerId(managerId);
        log.setCreatedBy(managerId);

        dbHR.saveLog(log);

        // In a real scenario, we might want to update the original log or add a new one.
        // For simplicity, we just add an audit log and expect the system to have a way to represent the 'corrected' state.
        // Here we will assume we add a corrected log entry of the specified type.
        AttendanceLog correctedLog = new AttendanceLog();
        correctedLog.setCompanyId(companyId);
        correctedLog.setBranchId(branchId);
        correctedLog.setEmployeeId(employeeId);
        correctedLog.setActionType(actionType);
        correctedLog.setActionTime(time);
        correctedLog.setRemarks("Manual entry by " + managerId);
        correctedLog.setCreatedBy(managerId);
        dbHR.saveLog(correctedLog);

        recalculateDailySummary(companyId, branchId, employeeId, date);
    }

    public void recalculateDailySummary(int companyId, int branchId, int employeeId, LocalDate date) {
        List<AttendanceLog> logs = dbHR.getAttendanceLogs(companyId, employeeId, java.sql.Date.valueOf(date));
        Shift shift = dbHR.getActiveShiftForEmployee(companyId, employeeId, java.sql.Date.valueOf(date));

        AttendanceDay day = dbHR.getAttendanceDay(companyId, employeeId, java.sql.Date.valueOf(date));
        if (day == null) {
            day = new AttendanceDay();
            day.setCompanyId(companyId);
            day.setBranchId(branchId);
            day.setEmployeeId(employeeId);
            day.setAttendanceDate(java.sql.Date.valueOf(date));
        }

        Timestamp clockIn = null;
        Timestamp clockOut = null;
        long breakMillis = 0;
        Timestamp breakStart = null;

        for (AttendanceLog log : logs) {
            switch (log.getActionType()) {
                case "CLOCK_IN":
                    if (clockIn == null) clockIn = log.getActionTime();
                    break;
                case "CLOCK_OUT":
                    clockOut = log.getActionTime();
                    break;
                case "BREAK_START":
                    breakStart = log.getActionTime();
                    break;
                case "BREAK_END":
                    if (breakStart != null) {
                        breakMillis += (log.getActionTime().getTime() - breakStart.getTime());
                        breakStart = null;
                    }
                    break;
            }
        }

        day.setClockIn(clockIn);
        day.setClockOut(clockOut);
        day.setBreakMinutes((int) (breakMillis / 60000));

        if (clockIn != null && clockOut != null) {
            long workMillis = clockOut.getTime() - clockIn.getTime();
            
            // Overnight shift support
            if (workMillis < 0) {
                workMillis += 24 * 60 * 60 * 1000;
            }
            
            day.setWorkingMinutes((int) (workMillis / 60000) - day.getBreakMinutes());

            if (shift != null) {
                calculateLateAndOvertime(day, shift, clockIn, clockOut);
            } else {
                day.setStatus("PRESENT");
            }
        } else if (clockIn != null) {
            day.setStatus("INCOMPLETE");
        } else {
            day.setStatus("ABSENT");
        }

        dbHR.upsertAttendanceDay(day);
    }

    private void calculateLateAndOvertime(AttendanceDay day, Shift shift, Timestamp clockIn, Timestamp clockOut) {
        LocalTime shiftStart = shift.getStartTime().toLocalTime();
        LocalTime shiftEnd = shift.getEndTime().toLocalTime();
        
        LocalDateTime actualIn = clockIn.toLocalDateTime();
        LocalDateTime expectedIn = LocalDateTime.of(actualIn.toLocalDate(), shiftStart);
        
        // Late calculation
        Duration late = Duration.between(expectedIn, actualIn);
        if (late.toMinutes() > shift.getGraceMinutes()) {
            day.setLateMinutes((int) late.toMinutes());
            day.setStatus("LATE");
        } else {
            day.setLateMinutes(0);
            day.setStatus("PRESENT");
        }

        // Overtime calculation
        LocalDateTime actualOut = clockOut.toLocalDateTime();
        LocalDateTime expectedOut = LocalDateTime.of(actualIn.toLocalDate(), shiftEnd);
        
        if (shiftEnd.isBefore(shiftStart)) {
            expectedOut = expectedOut.plusDays(1);
        }
        
        Duration overtime = Duration.between(expectedOut, actualOut);
        if (overtime.toMinutes() > 0) {
            day.setOvertimeMinutes((int) overtime.toMinutes());
        } else {
            day.setOvertimeMinutes(0);
        }

    }
}
