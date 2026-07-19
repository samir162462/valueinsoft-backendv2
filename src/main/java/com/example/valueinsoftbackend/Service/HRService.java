package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import com.example.valueinsoftbackend.Model.HR.AttendanceLog;
import com.example.valueinsoftbackend.Model.HR.AttendanceMonthDay;
import com.example.valueinsoftbackend.Model.HR.AttendanceMonthEmployee;
import com.example.valueinsoftbackend.Model.HR.AttendanceMonthResponse;
import com.example.valueinsoftbackend.Model.HR.AttendanceSelfStatus;
import com.example.valueinsoftbackend.Model.HR.AnnualLeavePeriod;
import com.example.valueinsoftbackend.Model.HR.Employee;
import com.example.valueinsoftbackend.Model.HR.EmployeeShift;
import com.example.valueinsoftbackend.Model.HR.Shift;
import com.example.valueinsoftbackend.Model.User;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class HRService {

    private static final String SYSTEM_ACTOR = "attendance-system";
    private static final int CLOCK_OUT_GRACE_MINUTES = 5;
    private static final List<String> ALLOWED_ACTIONS = List.of("CLOCK_IN", "CLOCK_OUT", "BREAK_START", "BREAK_END");

    private final DbHR dbHR;
    private final DbUsers dbUsers;
    private final DbBranch dbBranch;
    private final PasswordEncoder passwordEncoder;

    public HRService(DbHR dbHR, DbUsers dbUsers, DbBranch dbBranch, PasswordEncoder passwordEncoder) {
        this.dbHR = dbHR;
        this.dbUsers = dbUsers;
        this.dbBranch = dbBranch;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public int syncFromUsers(int companyId, int branchId, String actor) {
        assertBranchBelongsToCompany(companyId, branchId);
        return syncUsers(companyId, branchId, dbUsers.getUsersForCompany(companyId, branchId), actor);
    }

    private int syncUsers(int companyId, int fallbackBranchId, List<User> users, String actor) {
        int changed = 0;

        for (User user : users) {
            Employee employee = dbHR.getEmployeeByUser(companyId, user.getUserId());
            int targetBranchId = user.getBranchId() > 0
                    ? user.getBranchId()
                    : employee == null ? fallbackBranchId : employee.getBranchId();
            if (employee == null) {
                employee = new Employee();
                employee.setCompanyId(companyId);
                employee.setBranchId(targetBranchId);
                employee.setUserId(user.getUserId());
                employee.setEmployeeCode(String.valueOf(user.getUserId()));
                employee.setFirstName(preferredFirstName(user));
                employee.setLastName(user.getLastName());
                employee.setActive(true);
                employee.setCreatedBy(actor);
                employee.setUpdatedBy(actor);
                employee.setPinHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                employee.setId(dbHR.addEmployee(employee));
                changed++;
                continue;
            }

            boolean needsUpdate = employee.getBranchId() != targetBranchId
                    || !String.valueOf(user.getUserId()).equals(employee.getEmployeeCode())
                    || !preferredFirstName(user).equals(employee.getFirstName())
                    || !safeEquals(user.getLastName(), employee.getLastName())
                    || !employee.isActive();
            if (needsUpdate) {
                employee.setBranchId(targetBranchId);
                employee.setEmployeeCode(String.valueOf(user.getUserId()));
                employee.setFirstName(preferredFirstName(user));
                employee.setLastName(user.getLastName());
                employee.setActive(true);
                employee.setUpdatedBy(actor);
                dbHR.updateEmployee(employee);
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public int ensureBranchWorkspace(int companyId, int branchId, String actor) {
        int changes = syncFromUsers(companyId, branchId, actor);
        changes += dbHR.activateAllShifts(companyId, branchId, actor);

        if (dbHR.countActiveShifts(companyId, branchId) == 0) {
            Shift standardShift = new Shift();
            standardShift.setCompanyId(companyId);
            standardShift.setBranchId(branchId);
            standardShift.setShiftName("Standard Shift");
            standardShift.setStartTime(Time.valueOf("12:00:00"));
            standardShift.setEndTime(Time.valueOf("00:00:00"));
            standardShift.setGraceMinutes(15);
            standardShift.setActive(true);
            standardShift.setCreatedBy(SYSTEM_ACTOR);
            standardShift.setUpdatedBy(SYSTEM_ACTOR);
            dbHR.addShift(standardShift);
            changes++;
        }

        Shift onlyShift = dbHR.getOnlyActiveShift(companyId, branchId);
        if (onlyShift != null) {
            changes += dbHR.assignOnlyShiftToUnassignedEmployees(
                    companyId,
                    branchId,
                    onlyShift.getId(),
                    java.sql.Date.valueOf(LocalDate.now()),
                    SYSTEM_ACTOR
            );
        }
        return changes;
    }

    @Transactional
    public int ensureCompanyWorkspace(int companyId, String actor) {
        List<Branch> branches = dbBranch.getBranchByCompanyId(companyId);
        if (branches.isEmpty()) return 0;
        int changes = 0;
        changes += syncUsers(companyId, branches.get(0).getBranchID(), dbUsers.getUsersForCompany(companyId, null), actor);
        for (Branch branch : branches) {
            changes += ensureBranchWorkspace(companyId, branch.getBranchID(), actor);
        }
        return changes;
    }

    public List<Employee> getEmployees(int companyId, Integer branchId, boolean companyScope, String actor) {
        if (companyScope) {
            ensureCompanyWorkspace(companyId, actor);
            return dbHR.getAllEmployees(companyId, null);
        }
        ensureBranchWorkspace(companyId, requireBranchId(branchId), actor);
        return dbHR.getAllEmployees(companyId, branchId);
    }

    public List<Shift> getShifts(int companyId, int branchId, String actor) {
        ensureBranchWorkspace(companyId, branchId, actor);
        return dbHR.getAllShifts(companyId, branchId);
    }

    @Transactional
    public int createShift(int companyId, int branchId, Shift shift, String actor) {
        validateShift(shift);
        shift.setCompanyId(companyId);
        shift.setBranchId(branchId);
        shift.setActive(true);
        shift.setCreatedBy(actor);
        shift.setUpdatedBy(actor);
        return dbHR.addShift(shift);
    }

    @Transactional
    public Shift updateShift(int companyId, int branchId, int shiftId, Shift update, String actor) {
        Shift existing = dbHR.getShiftById(companyId, branchId, shiftId);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SHIFT_NOT_FOUND", "The shift was not found in this branch");
        }
        validateShift(update);
        existing.setShiftName(update.getShiftName().trim());
        existing.setStartTime(update.getStartTime());
        existing.setEndTime(update.getEndTime());
        existing.setGraceMinutes(update.getGraceMinutes());
        // Shifts are intentionally kept active. The attendance workspace
        // guarantees an active schedule for every branch.
        existing.setActive(true);
        existing.setUpdatedBy(actor);
        if (dbHR.updateShift(existing) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_UPDATE_CONFLICT", "The shift could not be updated");
        }
        return dbHR.getShiftById(companyId, branchId, shiftId);
    }

    public List<EmployeeShift> getAssignments(int companyId, int branchId, String actor) {
        ensureBranchWorkspace(companyId, branchId, actor);
        return dbHR.getAllAssignments(companyId, branchId);
    }

    private void validateShift(Shift shift) {
        if (shift == null || shift.getShiftName() == null || shift.getShiftName().isBlank()
                || shift.getShiftName().trim().length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIFT_NAME_INVALID", "A shift name of up to 120 characters is required");
        }
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIFT_TIME_REQUIRED", "Shift start and end times are required");
        }
        if (shift.getStartTime().equals(shift.getEndTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIFT_TIME_INVALID", "Shift start and end times cannot be the same");
        }
        if (shift.getGraceMinutes() < 0 || shift.getGraceMinutes() > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIFT_GRACE_INVALID", "Shift grace minutes must be between 0 and 180");
        }
    }

    public AttendanceMonthResponse getAttendanceMonth(int companyId, int branchId, YearMonth month,
                                                       boolean companyScope, String actor) {
        if (companyScope) ensureCompanyWorkspace(companyId, actor);
        else ensureBranchWorkspace(companyId, branchId, actor);

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        Integer scopedBranchId = companyScope ? null : branchId;
        java.sql.Date sqlStart = java.sql.Date.valueOf(monthStart);
        java.sql.Date sqlEnd = java.sql.Date.valueOf(monthEnd);

        List<Employee> employees = dbHR.getAllEmployees(companyId, scopedBranchId);
        List<AttendanceDay> attendanceDays = dbHR.getAttendanceReport(companyId, scopedBranchId, sqlStart, sqlEnd);
        List<EmployeeShift> assignments = dbHR.getAssignmentsForPeriod(companyId, scopedBranchId, sqlStart, sqlEnd);
        Map<Integer, Shift> shiftsById = new HashMap<>();
        for (Shift shift : dbHR.getShiftsForScope(companyId, scopedBranchId)) shiftsById.put(shift.getId(), shift);
        List<AnnualLeavePeriod> leavePeriods = dbHR.getApprovedAnnualLeaves(companyId, scopedBranchId, sqlStart, sqlEnd);

        Map<UserDateKey, AttendanceDay> attendanceByUserDate = new HashMap<>();
        for (AttendanceDay day : attendanceDays) {
            if (day.getUserId() != null) {
                attendanceByUserDate.put(new UserDateKey(day.getUserId(), day.getAttendanceDate().toLocalDate()), day);
            }
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceMonthDay> days = new ArrayList<>(month.lengthOfMonth());
        for (int dayNumber = 1; dayNumber <= month.lengthOfMonth(); dayNumber++) {
            LocalDate date = month.atDay(dayNumber);
            List<AttendanceMonthEmployee> dayEmployees = new ArrayList<>(employees.size());

            for (Employee employee : employees) {
                if (employee.getUserId() == null) continue;
                int userId = employee.getUserId();
                AttendanceDay attendance = attendanceByUserDate.get(new UserDateKey(userId, date));
                EmployeeShift assignment = findAssignment(assignments, userId, date);
                Shift shift = assignment == null ? null : shiftsById.get(assignment.getShiftId());
                int scheduledMinutes = scheduledMinutes(shift);
                int workingMinutes = effectiveWorkingMinutes(attendance, now);
                int breakMinutes = attendance == null ? 0 : attendance.getBreakMinutes();
                double percentage = scheduledMinutes == 0 ? 0
                        : Math.min(100.0, Math.round((workingMinutes * 1000.0 / scheduledMinutes)) / 10.0);
                String classification = classifyAttendanceDay(
                        userId, date, today, now, attendance, shift, leavePeriods,
                        percentage, breakMinutes
                );

                dayEmployees.add(new AttendanceMonthEmployee(
                        userId,
                        employee.getId(),
                        employee.getBranchId(),
                        employee.getFirstName(),
                        employee.getLastName(),
                        shift == null ? null : shift.getShiftName(),
                        classification,
                        percentage,
                        scheduledMinutes,
                        workingMinutes,
                        breakMinutes,
                        attendance == null ? 0 : attendance.getLateMinutes(),
                        attendance == null ? 0 : attendance.getOvertimeMinutes(),
                        attendance == null ? 0 : attendance.getSessionCount(),
                        attendance == null ? null : attendance.getClockIn(),
                        attendance == null ? null : attendance.getClockOut()
                ));
            }

            Map<String, Long> counts = new LinkedHashMap<>();
            for (AttendanceMonthEmployee item : dayEmployees) {
                counts.merge(item.classification(), 1L, Long::sum);
            }
            days.add(new AttendanceMonthDay(date, counts, dayEmployees));
        }
        return new AttendanceMonthResponse(month.toString(), employees.size(), days);
    }

    private EmployeeShift findAssignment(List<EmployeeShift> assignments, int userId, LocalDate date) {
        for (EmployeeShift assignment : assignments) {
            if (assignment.getUserId() == null || assignment.getUserId() != userId) continue;
            LocalDate from = assignment.getEffectiveFrom().toLocalDate();
            LocalDate to = assignment.getEffectiveTo() == null ? null : assignment.getEffectiveTo().toLocalDate();
            if (!date.isBefore(from) && (to == null || !date.isAfter(to))) return assignment;
        }
        return null;
    }

    private int scheduledMinutes(Shift shift) {
        if (shift == null) return 0;
        LocalTime start = shift.getStartTime().toLocalTime();
        LocalTime end = shift.getEndTime().toLocalTime();
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) minutes += 24 * 60;
        return (int) minutes;
    }

    private int effectiveWorkingMinutes(AttendanceDay attendance, LocalDateTime now) {
        if (attendance == null || attendance.getClockIn() == null) return 0;
        if (attendance.getClockOut() != null) return attendance.getWorkingMinutes();
        if ("ON_BREAK".equals(attendance.getStatus()) || attendance.getUpdatedAt() == null) {
            return attendance.getWorkingMinutes();
        }
        long elapsed = Duration.between(attendance.getUpdatedAt().toLocalDateTime(), now).toMinutes();
        if (elapsed < 0 || elapsed > 36 * 60) {
            return attendance.getWorkingMinutes();
        }
        return Math.max(0, attendance.getWorkingMinutes() + (int) elapsed);
    }

    private String classifyAttendanceDay(int userId, LocalDate date, LocalDate today, LocalDateTime now,
                                         AttendanceDay attendance, Shift shift,
                                         List<AnnualLeavePeriod> leavePeriods, double percentage,
                                         int breakMinutes) {
        boolean annualLeave = leavePeriods.stream().anyMatch(leave -> leave.userId() == userId
                && !date.isBefore(leave.startDate()) && !date.isAfter(leave.endDate()));
        if (annualLeave) return "ANNUAL_LEAVE";
        if (date.isAfter(today)) return "UPCOMING";
        if (shift == null) return "UNSCHEDULED";

        LocalDateTime scheduledStart = LocalDateTime.of(date, shift.getStartTime().toLocalTime());
        if (date.equals(today) && attendance == null && now.isBefore(scheduledStart)) return "UPCOMING";
        if (attendance == null || attendance.getClockIn() == null) return "ABSENT";
        if (breakMinutes > 60) return "EXCESSIVE_BREAK";
        return percentage >= 95.0 ? "FULL_ATTENDANCE" : "BELOW_TARGET";
    }

    private record UserDateKey(int userId, LocalDate date) {
    }

    public Employee getEmployeeByUsername(int companyId, String principalName) {
        User user = resolveUser(principalName);
        if (user == null) return null;
        return dbHR.getEmployeeByUser(companyId, user.getUserId());
    }

    @Transactional
    public void assignShiftByUser(int companyId, int branchId, EmployeeShift assignment, String actor) {
        ensureBranchWorkspace(companyId, branchId, actor);
        if (assignment.getUserId() == null || assignment.getUserId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED", "A company user must be selected");
        }
        Employee employee = dbHR.getEmployeeByUser(companyId, branchId, assignment.getUserId());
        if (employee == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_NOT_IN_BRANCH", "The selected user is not active in this branch");
        }
        Shift shift = dbHR.getShiftById(companyId, branchId, assignment.getShiftId());
        if (shift == null || !shift.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHIFT_NOT_ACTIVE", "The selected shift is not active in this branch");
        }
        java.sql.Date effectiveFrom = assignment.getEffectiveFrom() == null
                ? java.sql.Date.valueOf(LocalDate.now())
                : assignment.getEffectiveFrom();
        dbHR.closeOpenAssignments(companyId, branchId, assignment.getUserId(), effectiveFrom, actor);
        assignment.setCompanyId(companyId);
        assignment.setBranchId(branchId);
        assignment.setEmployeeId(employee.getId());
        assignment.setEffectiveFrom(effectiveFrom);
        assignment.setCreatedBy(actor);
        assignment.setUpdatedBy(actor);
        dbHR.assignShift(assignment);
    }

    @Transactional
    public void processAttendanceAction(int companyId, int branchId, String code, String pin, String actionType,
                                        String source, String deviceId, String ipAddress, String userAgent) {
        String normalizedAction = normalizeAction(actionType);
        Employee employee = dbHR.getEmployeeByCode(companyId, branchId, code);
        if (employee == null || employee.getUserId() == null || !employee.isActive()
                || !passwordEncoder.matches(pin, employee.getPinHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid employee code or PIN");
        }
        dbHR.lockAttendanceUser(companyId, employee.getUserId());
        AttendanceDay openDay = dbHR.getOpenAttendanceDayByUser(companyId, branchId, employee.getUserId());
        LocalDate attendanceDate = openDay == null ? LocalDate.now() : openDay.getAttendanceDate().toLocalDate();
        AttendanceSelfStatus before = buildSelfStatus(companyId, branchId, employee, attendanceDate);
        assertAvailableAction(before, normalizedAction);
        saveAttendanceAction(companyId, branchId, employee, attendanceDate, normalizedAction, source, deviceId, ipAddress, userAgent, "kiosk");
    }

    public AttendanceSelfStatus getSelfStatus(int companyId, int branchId, String principalName) {
        User user = resolveRequiredUser(principalName);
        assertBranchBelongsToCompany(companyId, branchId);

        if (user.getBranchId() > 0 && user.getBranchId() != branchId) {
            return ineligibleStatus(companyId, branchId, user.getUserId(), "USER_BRANCH_MISMATCH");
        }

        if (user.getBranchId() > 0) {
            ensureBranchWorkspace(companyId, branchId, principalName);
        } else {
            boolean isCompanyUser = dbUsers.getUsersForCompany(companyId, null).stream()
                    .anyMatch(companyUser -> companyUser.getUserId() == user.getUserId());
            if (!isCompanyUser) {
                return ineligibleStatus(companyId, branchId, user.getUserId(), "USER_NOT_IN_COMPANY");
            }
            syncUsers(companyId, branchId, List.of(user), principalName);
            Employee companyEmployee = dbHR.getEmployeeByUser(companyId, user.getUserId());
            if (companyEmployee != null && companyEmployee.getBranchId() != branchId) {
                return ineligibleStatus(companyId, branchId, user.getUserId(), "USER_BRANCH_MISMATCH");
            }
            ensureBranchWorkspace(companyId, branchId, principalName);
        }
        Employee employee = dbHR.getEmployeeByUser(companyId, branchId, user.getUserId());
        if (employee == null) {
            return ineligibleStatus(companyId, branchId, user.getUserId(), "USER_NOT_SYNCHRONIZED");
        }
        AttendanceDay openDay = dbHR.getOpenAttendanceDayByUser(companyId, branchId, employee.getUserId());
        LocalDate attendanceDate = openDay == null ? LocalDate.now() : openDay.getAttendanceDate().toLocalDate();
        return buildSelfStatus(companyId, branchId, employee, attendanceDate);
    }

    @Transactional
    public AttendanceSelfStatus processSelfAttendanceAction(int companyId, int branchId, String principalName,
                                                            String actionType, String source, String deviceId,
                                                            String ipAddress, String userAgent) {
        String normalizedAction = normalizeAction(actionType);
        User actor = resolveRequiredUser(principalName);
        dbHR.lockAttendanceUser(companyId, actor.getUserId());
        AttendanceSelfStatus before = getSelfStatus(companyId, branchId, principalName);
        if (!before.isEligible()) {
            throw new ApiException(HttpStatus.FORBIDDEN, before.getIneligibleReason(), "Attendance is not available in this branch");
        }
        assertAvailableAction(before, normalizedAction);

        Employee employee = dbHR.getEmployeeByUser(companyId, branchId, before.getUserId());
        LocalDate attendanceDate = before.getAttendanceDate();
        saveAttendanceAction(companyId, branchId, employee, attendanceDate, normalizedAction, source, deviceId, ipAddress, userAgent, principalName);
        return buildSelfStatus(companyId, branchId, employee, attendanceDate);
    }

    private void assertAvailableAction(AttendanceSelfStatus status, String actionType) {
        if (!status.getAvailableActions().contains(actionType)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ATTENDANCE_TRANSITION",
                    "The requested attendance action is not valid for the current state");
        }
    }

    private void saveAttendanceAction(int companyId, int branchId, Employee employee, LocalDate attendanceDate, String actionType,
                                      String source, String deviceId, String ipAddress, String userAgent, String actor) {
        AttendanceLog log = new AttendanceLog();
        log.setCompanyId(companyId);
        log.setBranchId(branchId);
        log.setEmployeeId(employee.getId());
        log.setUserId(employee.getUserId());
        log.setActionType(actionType);
        log.setActionTime(new Timestamp(System.currentTimeMillis()));
        log.setSource(source == null || source.isBlank() ? "web-self" : source);
        log.setDeviceId(deviceId);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedBy(actor);
        dbHR.saveLog(log);
        recalculateDailySummary(companyId, branchId, employee.getId(), attendanceDate);
    }

    @Transactional
    public void manualCorrection(int companyId, int branchId, int userId, LocalDate date, String actionType,
                                 Timestamp time, String reason, String managerId) {
        String normalizedAction = normalizeAction(actionType);
        Employee employee = dbHR.getEmployeeByUser(companyId, branchId, userId);
        if (employee == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_NOT_IN_BRANCH", "The selected user is not active in this branch");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORRECTION_REASON_REQUIRED", "A correction reason is required");
        }

        AttendanceLog auditLog = new AttendanceLog();
        auditLog.setCompanyId(companyId);
        auditLog.setBranchId(branchId);
        auditLog.setEmployeeId(employee.getId());
        auditLog.setUserId(userId);
        auditLog.setActionType("MANUAL_CORRECTION");
        auditLog.setActionTime(new Timestamp(System.currentTimeMillis()));
        auditLog.setRemarks("Correction for " + normalizedAction + " at " + time);
        auditLog.setCorrectionReason(reason.trim());
        auditLog.setManagerId(managerId);
        auditLog.setCreatedBy(managerId);
        dbHR.saveLog(auditLog);

        AttendanceLog correctedLog = new AttendanceLog();
        correctedLog.setCompanyId(companyId);
        correctedLog.setBranchId(branchId);
        correctedLog.setEmployeeId(employee.getId());
        correctedLog.setUserId(userId);
        correctedLog.setActionType(normalizedAction);
        correctedLog.setActionTime(time);
        correctedLog.setSource("manual-correction");
        correctedLog.setRemarks("Manual entry by " + managerId);
        correctedLog.setCorrectionReason(reason.trim());
        correctedLog.setManagerId(managerId);
        correctedLog.setCreatedBy(managerId);
        dbHR.saveLog(correctedLog);
        recalculateDailySummary(companyId, branchId, employee.getId(), date);
    }

    public void recalculateDailySummary(int companyId, int branchId, int employeeId, LocalDate date) {
        Employee employee = dbHR.getEmployeeById(companyId, branchId, employeeId);
        if (employee == null || employee.getUserId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED", "Attendance must be linked to a company user");
        }
        Shift shift = dbHR.getActiveShiftForEmployee(companyId, employeeId, java.sql.Date.valueOf(date));
        Timestamp logWindowStart = Timestamp.valueOf(date.atStartOfDay());
        Timestamp logWindowEnd = Timestamp.valueOf(date.plusDays(2).atStartOfDay());
        List<AttendanceLog> logs = dbHR.getAttendanceLogsBetween(companyId, employeeId, logWindowStart, logWindowEnd);
        AttendanceAggregate aggregate = aggregateAttendance(date, logs, new Timestamp(System.currentTimeMillis()));

        AttendanceDay day = dbHR.getAttendanceDay(companyId, employeeId, java.sql.Date.valueOf(date));
        if (day == null) {
            day = new AttendanceDay();
            day.setCompanyId(companyId);
            day.setBranchId(branchId);
            day.setEmployeeId(employeeId);
            day.setAttendanceDate(java.sql.Date.valueOf(date));
        }
        day.setUserId(employee.getUserId());
        day.setClockIn(aggregate.open() ? aggregate.currentClockIn() : aggregate.firstClockIn());
        day.setClockOut(aggregate.open() ? null : aggregate.lastClockOut());
        day.setWorkingMinutes(toMinutes(aggregate.workingMillis()));
        day.setBreakMinutes(toMinutes(aggregate.breakMillis()));
        day.setLateMinutes(0);
        day.setSessionCount(aggregate.sessionCount());
        int scheduled = scheduledMinutes(shift);
        day.setOvertimeMinutes(scheduled == 0 ? 0 : Math.max(0, day.getWorkingMinutes() - scheduled));

        if (aggregate.open()) {
            day.setStatus(aggregate.onBreak() ? "ON_BREAK" : "INCOMPLETE");
        } else if (aggregate.sessionCount() > 0) {
            day.setStatus("PRESENT");
        } else {
            day.setStatus("ABSENT");
        }
        dbHR.upsertAttendanceDay(day);
    }

    static AttendanceAggregate aggregateAttendance(LocalDate attendanceDate,
                                                    List<AttendanceLog> logs,
                                                    Timestamp calculatedAt) {
        Timestamp firstClockIn = null;
        Timestamp lastClockOut = null;
        Timestamp currentClockIn = null;
        Timestamp breakStart = null;
        long currentSessionBreakMillis = 0;
        long totalBreakMillis = 0;
        long totalWorkingMillis = 0;
        int sessionCount = 0;

        for (AttendanceLog log : logs) {
            Timestamp actionTime = log.getActionTime();
            if (actionTime == null) continue;
            String actionType = log.getActionType();

            if ("CLOCK_IN".equals(actionType)) {
                LocalDate sessionDate = actionTime.toLocalDateTime().toLocalDate();
                if (sessionDate.isAfter(attendanceDate) && currentClockIn == null) break;
                if (!sessionDate.equals(attendanceDate) || currentClockIn != null) continue;
                currentClockIn = actionTime;
                currentSessionBreakMillis = 0;
                breakStart = null;
                sessionCount++;
                if (firstClockIn == null) firstClockIn = actionTime;
                continue;
            }

            if (currentClockIn == null || !actionTime.after(currentClockIn)) continue;
            switch (actionType) {
                case "BREAK_START" -> {
                    if (breakStart == null) breakStart = actionTime;
                }
                case "BREAK_END" -> {
                    if (breakStart != null && actionTime.after(breakStart)) {
                        currentSessionBreakMillis += actionTime.getTime() - breakStart.getTime();
                        breakStart = null;
                    }
                }
                case "CLOCK_OUT" -> {
                    if (breakStart != null) {
                        currentSessionBreakMillis += actionTime.getTime() - breakStart.getTime();
                        breakStart = null;
                    }
                    totalBreakMillis += currentSessionBreakMillis;
                    totalWorkingMillis += Math.max(0,
                            actionTime.getTime() - currentClockIn.getTime() - currentSessionBreakMillis);
                    lastClockOut = actionTime;
                    currentClockIn = null;
                    currentSessionBreakMillis = 0;
                }
                default -> { }
            }
        }

        boolean onBreak = currentClockIn != null && breakStart != null;
        if (currentClockIn != null) {
            Timestamp effectiveEnd = calculatedAt.before(currentClockIn) ? currentClockIn : calculatedAt;
            long openBreakMillis = currentSessionBreakMillis;
            if (breakStart != null && effectiveEnd.after(breakStart)) {
                openBreakMillis += effectiveEnd.getTime() - breakStart.getTime();
            }
            totalBreakMillis += openBreakMillis;
            totalWorkingMillis += Math.max(0,
                    effectiveEnd.getTime() - currentClockIn.getTime() - openBreakMillis);
        }

        return new AttendanceAggregate(
                firstClockIn, lastClockOut, currentClockIn,
                Math.max(0, totalWorkingMillis), Math.max(0, totalBreakMillis),
                sessionCount, currentClockIn != null, onBreak
        );
    }

    private int toMinutes(long milliseconds) {
        return Math.max(0, Math.toIntExact(milliseconds / 60_000));
    }

    static record AttendanceAggregate(
            Timestamp firstClockIn,
            Timestamp lastClockOut,
            Timestamp currentClockIn,
            long workingMillis,
            long breakMillis,
            int sessionCount,
            boolean open,
            boolean onBreak
    ) { }

    private void recalculateDailySummaryLegacy(int companyId, int branchId, int employeeId, LocalDate date) {
        Employee employee = dbHR.getEmployeeById(companyId, branchId, employeeId);
        if (employee == null || employee.getUserId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED", "Attendance must be linked to a company user");
        }
        Shift shift = dbHR.getActiveShiftForEmployee(companyId, employeeId, java.sql.Date.valueOf(date));
        // Always scan two days: a clock-out for this attendance day may legitimately be
        // recorded after midnight (overnight shifts or late clock-outs on an open day).
        // The loop below stops at the first CLOCK_IN of a new session, so logs belonging
        // to the following day are never attributed to this one.
        Timestamp logWindowStart = Timestamp.valueOf(date.atStartOfDay());
        Timestamp logWindowEnd = Timestamp.valueOf(date.plusDays(2).atStartOfDay());
        List<AttendanceLog> logs = dbHR.getAttendanceLogsBetween(companyId, employeeId, logWindowStart, logWindowEnd);

        AttendanceDay day = dbHR.getAttendanceDay(companyId, employeeId, java.sql.Date.valueOf(date));
        if (day == null) {
            day = new AttendanceDay();
            day.setCompanyId(companyId);
            day.setBranchId(branchId);
            day.setEmployeeId(employeeId);
            day.setAttendanceDate(java.sql.Date.valueOf(date));
        }
        day.setUserId(employee.getUserId());

        Timestamp clockIn = null;
        Timestamp clockOut = null;
        long breakMillis = 0;
        Timestamp breakStart = null;

        for (AttendanceLog log : logs) {
            Timestamp actionTime = log.getActionTime();
            // A CLOCK_IN after this day's session is already closed starts the next
            // day's session — stop so its logs are not attributed to this day.
            if ("CLOCK_IN".equals(log.getActionType()) && clockIn != null && clockOut != null && actionTime.after(clockOut)) {
                break;
            }
            switch (log.getActionType()) {
                case "CLOCK_IN" -> { if (clockIn == null) clockIn = actionTime; }
                case "CLOCK_OUT" -> { if (clockIn != null && actionTime.after(clockIn)) clockOut = actionTime; }
                case "BREAK_START" -> { if (breakStart == null && clockIn != null && clockOut == null) breakStart = actionTime; }
                case "BREAK_END" -> {
                    if (breakStart != null && actionTime.after(breakStart)) {
                        breakMillis += actionTime.getTime() - breakStart.getTime();
                        breakStart = null;
                    }
                }
                default -> { }
            }
        }

        day.setClockIn(clockIn);
        day.setClockOut(clockOut);
        day.setBreakMinutes((int) (breakMillis / 60_000));
        day.setLateMinutes(0);
        day.setOvertimeMinutes(0);
        day.setWorkingMinutes(0);

        if (clockIn != null && clockOut != null) {
            long workMillis = Math.max(0, clockOut.getTime() - clockIn.getTime());
            day.setWorkingMinutes(Math.max(0, (int) (workMillis / 60_000) - day.getBreakMinutes()));
            if (shift != null) calculateLateAndOvertime(day, shift, clockIn, clockOut);
            else day.setStatus("PRESENT");
        } else if (clockIn != null) {
            day.setStatus(breakStart == null ? "INCOMPLETE" : "ON_BREAK");
        } else {
            day.setStatus("ABSENT");
        }
        dbHR.upsertAttendanceDay(day);
    }

    private AttendanceSelfStatus buildSelfStatus(int companyId, int branchId, Employee employee, LocalDate date) {
        AttendanceDay day = dbHR.getAttendanceDayByUser(companyId, branchId, employee.getUserId(), java.sql.Date.valueOf(date));
        boolean onBreak = day != null && "ON_BREAK".equals(day.getStatus());
        boolean sessionOpen = day != null && day.getClockIn() != null && day.getClockOut() == null
                && ("INCOMPLETE".equals(day.getStatus()) || onBreak);

        Timestamp clockIn = day == null ? null : day.getClockIn();
        Timestamp clockOut = day == null ? null : day.getClockOut();
        List<String> availableActions = new ArrayList<>();
        String primaryAction = null;
        if (sessionOpen && onBreak) {
            availableActions.add("BREAK_END");
            primaryAction = "BREAK_END";
        } else if (sessionOpen) {
            availableActions.add("BREAK_START");
            availableActions.add("CLOCK_OUT");
            primaryAction = "CLOCK_OUT";
        } else {
            availableActions.add("CLOCK_IN");
            primaryAction = "CLOCK_IN";
        }

        Shift shift = dbHR.getActiveShiftForEmployee(companyId, employee.getId(), java.sql.Date.valueOf(date));
        Timestamp calculatedAt = new Timestamp(System.currentTimeMillis());
        int workingMinutes = effectiveWorkingMinutes(day, calculatedAt.toLocalDateTime());
        Timestamp scheduledShiftStart = null;
        Timestamp scheduledShiftEnd = null;
        boolean shiftEndingSoon = false;
        if (shift != null) {
            LocalDateTime scheduledStart = LocalDateTime.of(date, shift.getStartTime().toLocalTime());
            LocalDateTime scheduledEnd = LocalDateTime.of(date, shift.getEndTime().toLocalTime());
            if (!scheduledEnd.isAfter(scheduledStart)) scheduledEnd = scheduledEnd.plusDays(1);
            scheduledShiftStart = Timestamp.valueOf(scheduledStart);
            scheduledShiftEnd = Timestamp.valueOf(scheduledEnd);
            // Warn based on actual worked time, not wall-clock: only when worked
            // minutes are within the grace window of the shift's required duration.
            long shiftDurationMinutes = Duration.between(scheduledStart, scheduledEnd).toMinutes();
            shiftEndingSoon = sessionOpen
                    && shiftDurationMinutes > 0
                    && workingMinutes >= shiftDurationMinutes - CLOCK_OUT_GRACE_MINUTES;
        }
        return new AttendanceSelfStatus(
                employee.getUserId(), employee.getId(), companyId, branchId, date, true, null,
                clockIn, clockOut, onBreak, day != null && day.getSessionCount() > 0 && !sessionOpen,
                day == null ? "NOT_STARTED" : day.getStatus(), primaryAction, availableActions,
                shift == null ? null : shift.getId(), shift == null ? null : shift.getShiftName(),
                scheduledShiftStart, scheduledShiftEnd, CLOCK_OUT_GRACE_MINUTES, shiftEndingSoon,
                day == null ? 0 : day.getSessionCount(), workingMinutes,
                day == null ? 0 : day.getBreakMinutes(), calculatedAt
        );
    }

    private AttendanceSelfStatus ineligibleStatus(int companyId, int branchId, int userId, String reason) {
        return new AttendanceSelfStatus(userId, 0, companyId, branchId, LocalDate.now(), false, reason,
                null, null, false, false, "INELIGIBLE", null, List.of(), null, null,
                null, null, CLOCK_OUT_GRACE_MINUTES, false, 0, 0, 0,
                new Timestamp(System.currentTimeMillis()));
    }

    private void calculateLateAndOvertime(AttendanceDay day, Shift shift, Timestamp clockIn, Timestamp clockOut) {
        LocalTime shiftStart = shift.getStartTime().toLocalTime();
        LocalTime shiftEnd = shift.getEndTime().toLocalTime();
        LocalDateTime actualIn = clockIn.toLocalDateTime();
        LocalDateTime expectedIn = LocalDateTime.of(actualIn.toLocalDate(), shiftStart);
        long lateMinutes = Duration.between(expectedIn, actualIn).toMinutes();
        if (lateMinutes > shift.getGraceMinutes()) {
            day.setLateMinutes((int) lateMinutes);
            day.setStatus("LATE");
        } else {
            day.setLateMinutes(0);
            day.setStatus("PRESENT");
        }

        LocalDateTime expectedOut = LocalDateTime.of(actualIn.toLocalDate(), shiftEnd);
        if (!shiftEnd.isAfter(shiftStart)) expectedOut = expectedOut.plusDays(1);
        long overtimeMinutes = Duration.between(expectedOut, clockOut.toLocalDateTime()).toMinutes();
        day.setOvertimeMinutes((int) Math.max(0, overtimeMinutes));
    }

    private String normalizeAction(String actionType) {
        String normalized = actionType == null ? "" : actionType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ACTIONS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTENDANCE_ACTION", "Unsupported attendance action");
        }
        return normalized;
    }

    private User resolveRequiredUser(String principalName) {
        User user = resolveUser(principalName);
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Authenticated user was not found");
        return user;
    }

    private User resolveUser(String principalName) {
        if (principalName == null || principalName.isBlank()) return null;
        User user = dbUsers.getUser(principalName);
        if (user != null) return user;
        String baseName = principalName.contains(" : ") ? principalName.split(" : ", 2)[0].trim() : principalName.trim();
        return dbUsers.getUser(baseName);
    }

    private void assertBranchBelongsToCompany(int companyId, int branchId) {
        Branch branch = dbBranch.getBranchById(branchId);
        if (branch.getBranchOfCompanyId() != companyId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_COMPANY_MISMATCH", "The branch does not belong to this company");
        }
    }

    private int requireBranchId(Integer branchId) {
        if (branchId == null || branchId <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_REQUIRED", "A branch is required");
        return branchId;
    }

    private String preferredFirstName(User user) {
        return user.getFirstName() == null || user.getFirstName().isBlank() ? user.getUserName() : user.getFirstName();
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
