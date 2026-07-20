package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.HR.Employee;
import com.example.valueinsoftbackend.Model.HR.EmployeeShift;
import com.example.valueinsoftbackend.Model.HR.AnnualLeavePeriod;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import com.example.valueinsoftbackend.Model.HR.AttendanceLog;
import com.example.valueinsoftbackend.Model.HR.AttendanceMonthResponse;
import com.example.valueinsoftbackend.Model.HR.AttendanceSelfStatus;
import com.example.valueinsoftbackend.Model.HR.Shift;
import com.example.valueinsoftbackend.Model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HRServiceTest {

    private static final int COMPANY_ID = 1095;
    private static final int BRANCH_ID = 1095;

    private DbHR dbHR;
    private DbUsers dbUsers;
    private DbBranch dbBranch;
    private PasswordEncoder passwordEncoder;
    private HRService service;

    @BeforeEach
    void setUp() {
        dbHR = Mockito.mock(DbHR.class);
        dbUsers = Mockito.mock(DbUsers.class);
        dbBranch = Mockito.mock(DbBranch.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        service = new HRService(dbHR, dbUsers, dbBranch, passwordEncoder);

        when(dbBranch.getBranchById(BRANCH_ID))
                .thenReturn(new Branch(BRANCH_ID, COMPANY_ID, "Zag branch", "Zagazig", null));
    }

    @Test
    void syncCreatesEmployeeProfileFromCompanyUserIdentity() {
        User user = new User(42, "sam", "ignored", "sam@example.com", "Sam", "Owner",
                "01000000000", "Owner", 1, BRANCH_ID, null);
        when(dbUsers.getUsersForCompany(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(user));
        when(dbHR.getEmployeeByUser(COMPANY_ID, 42)).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("random-legacy-pin-hash");
        when(dbHR.addEmployee(any())).thenReturn(7);

        assertEquals(1, service.syncFromUsers(COMPANY_ID, BRANCH_ID, "sam"));

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(dbHR).addEmployee(employeeCaptor.capture());
        Employee employee = employeeCaptor.getValue();
        assertEquals(42, employee.getUserId());
        assertEquals("42", employee.getEmployeeCode());
        assertEquals(BRANCH_ID, employee.getBranchId());
        assertTrue(employee.isActive());
    }

    @Test
    void ensureCreatesStandardShiftAndAssignsItWhenBranchHasNone() {
        when(dbUsers.getUsersForCompany(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());
        when(dbHR.countActiveShifts(COMPANY_ID, BRANCH_ID)).thenReturn(0);
        Shift onlyShift = new Shift();
        onlyShift.setId(31);
        when(dbHR.getOnlyActiveShift(COMPANY_ID, BRANCH_ID)).thenReturn(onlyShift);
        when(dbHR.assignOnlyShiftToUnassignedEmployees(eq(COMPANY_ID), eq(BRANCH_ID), eq(31), any(), eq("attendance-system")))
                .thenReturn(3);

        assertEquals(4, service.ensureBranchWorkspace(COMPANY_ID, BRANCH_ID, "sam"));

        ArgumentCaptor<Shift> shiftCaptor = ArgumentCaptor.forClass(Shift.class);
        verify(dbHR).addShift(shiftCaptor.capture());
        Shift shift = shiftCaptor.getValue();
        assertEquals("Standard Shift", shift.getShiftName());
        assertEquals(Time.valueOf("12:00:00"), shift.getStartTime());
        assertEquals(Time.valueOf("00:00:00"), shift.getEndTime());
        assertTrue(shift.isActive());
        verify(dbHR).assignOnlyShiftToUnassignedEmployees(eq(COMPANY_ID), eq(BRANCH_ID), eq(31), any(), eq("attendance-system"));
    }

    @Test
    void updateShiftChangesOnlyTheScopedScheduleAndKeepsItActive() {
        Shift existing = new Shift();
        existing.setId(31);
        existing.setCompanyId(COMPANY_ID);
        existing.setBranchId(BRANCH_ID);
        existing.setShiftName("Main Shift");
        existing.setStartTime(Time.valueOf("09:00:00"));
        existing.setEndTime(Time.valueOf("17:00:00"));
        existing.setGraceMinutes(15);
        existing.setActive(true);

        Shift requested = new Shift();
        requested.setShiftName("Evening Shift");
        requested.setStartTime(Time.valueOf("13:00:00"));
        requested.setEndTime(Time.valueOf("21:00:00"));
        requested.setGraceMinutes(10);

        when(dbHR.getShiftById(COMPANY_ID, BRANCH_ID, 31)).thenReturn(existing);
        when(dbHR.updateShift(any(Shift.class))).thenReturn(1);

        Shift saved = service.updateShift(COMPANY_ID, BRANCH_ID, 31, requested, "sam");

        ArgumentCaptor<Shift> shiftCaptor = ArgumentCaptor.forClass(Shift.class);
        verify(dbHR).updateShift(shiftCaptor.capture());
        Shift updated = shiftCaptor.getValue();
        assertEquals("Evening Shift", updated.getShiftName());
        assertEquals(Time.valueOf("13:00:00"), updated.getStartTime());
        assertEquals(Time.valueOf("21:00:00"), updated.getEndTime());
        assertEquals(10, updated.getGraceMinutes());
        assertTrue(updated.isActive());
        assertEquals("sam", updated.getUpdatedBy());
        assertEquals(updated, saved);
    }

    @Test
    void companySyncIncludesBranchlessCompanyUserWithDeterministicHomeBranch() {
        User owner = new User(1054, "sam0001", "ignored", "owner@example.com", "Sam", "Owner",
                "01000000000", "Owner", 1, 0, null);
        when(dbBranch.getBranchByCompanyId(COMPANY_ID))
                .thenReturn(List.of(new Branch(BRANCH_ID, COMPANY_ID, "Zag branch", "Zagazig", null)));
        when(dbUsers.getUsersForCompany(COMPANY_ID, null)).thenReturn(List.of(owner));
        when(dbUsers.getUsersForCompany(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());
        when(dbHR.getEmployeeByUser(COMPANY_ID, 1054)).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("random-legacy-pin-hash");
        when(dbHR.addEmployee(any())).thenReturn(9);
        when(dbHR.countActiveShifts(COMPANY_ID, BRANCH_ID)).thenReturn(1);

        assertEquals(1, service.ensureCompanyWorkspace(COMPANY_ID, "sam0001"));

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(dbHR).addEmployee(employeeCaptor.capture());
        assertEquals(1054, employeeCaptor.getValue().getUserId());
        assertEquals(BRANCH_ID, employeeCaptor.getValue().getBranchId());
    }

    @Test
    void selfStatusKeepsOvernightAttendanceOpenAndSignalsCheckoutWindow() {
        User user = new User(42, "sam", "ignored", "sam@example.com", "Sam", "Owner",
                "01000000000", "Owner", 1, BRANCH_ID, null);
        Employee employee = new Employee(7, COMPANY_ID, BRANCH_ID, "42", "hash", "Sam", "Owner",
                42, true, null, "system", null, "system");
        LocalDate attendanceDate = LocalDate.now().minusDays(1);
        AttendanceDay openDay = new AttendanceDay();
        openDay.setCompanyId(COMPANY_ID);
        openDay.setBranchId(BRANCH_ID);
        openDay.setEmployeeId(7);
        openDay.setUserId(42);
        openDay.setAttendanceDate(java.sql.Date.valueOf(attendanceDate));
        openDay.setClockIn(Timestamp.valueOf(attendanceDate.atTime(12, 0)));
        openDay.setWorkingMinutes(12 * 60 - 5);
        openDay.setStatus("INCOMPLETE");
        Shift shift = new Shift();
        shift.setId(31);
        shift.setShiftName("Standard Shift");
        shift.setStartTime(Time.valueOf("12:00:00"));
        shift.setEndTime(Time.valueOf("00:00:00"));

        when(dbUsers.getUser("sam")).thenReturn(user);
        when(dbUsers.getUsersForCompany(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(user));
        when(dbHR.getEmployeeByUser(COMPANY_ID, 42)).thenReturn(employee);
        when(dbHR.getEmployeeByUser(COMPANY_ID, BRANCH_ID, 42)).thenReturn(employee);
        when(dbHR.countActiveShifts(COMPANY_ID, BRANCH_ID)).thenReturn(1);
        when(dbHR.getOpenAttendanceDayByUser(COMPANY_ID, BRANCH_ID, 42)).thenReturn(openDay);
        when(dbHR.getAttendanceDayByUser(COMPANY_ID, BRANCH_ID, 42, java.sql.Date.valueOf(attendanceDate))).thenReturn(openDay);
        when(dbHR.getActiveShiftForEmployee(COMPANY_ID, 7, java.sql.Date.valueOf(attendanceDate))).thenReturn(shift);

        AttendanceSelfStatus status = service.getSelfStatus(COMPANY_ID, BRANCH_ID, "sam");

        assertEquals(attendanceDate, status.getAttendanceDate());
        assertEquals(Timestamp.valueOf(LocalDate.now().atStartOfDay()), status.getScheduledShiftEnd());
        assertEquals(5, status.getClockOutGraceMinutes());
        assertTrue(status.isShiftEndingSoon());
    }

    @Test
    void aggregatesEveryClockInOutPairStartedOnTheSameDay() {
        LocalDate date = LocalDate.of(2026, 7, 19);
        List<AttendanceLog> logs = List.of(
                attendanceLog("CLOCK_IN", date.atTime(8, 0)),
                attendanceLog("CLOCK_OUT", date.atTime(10, 0)),
                attendanceLog("CLOCK_IN", date.atTime(11, 15)),
                attendanceLog("BREAK_START", date.atTime(11, 45)),
                attendanceLog("BREAK_END", date.atTime(12, 0)),
                attendanceLog("CLOCK_OUT", date.atTime(13, 15)),
                attendanceLog("CLOCK_IN", date.plusDays(1).atTime(8, 0))
        );

        HRService.AttendanceAggregate result = HRService.aggregateAttendance(
                date, logs, Timestamp.valueOf(date.atTime(14, 0)));

        assertEquals(2, result.sessionCount());
        assertEquals(225, result.workingMillis() / 60_000);
        assertEquals(15, result.breakMillis() / 60_000);
        assertEquals(Timestamp.valueOf(date.atTime(13, 15)), result.lastClockOut());
        assertFalse(result.open());
    }

    @Test
    void keepsCompletedMinutesWhenAnotherSessionStartsLaterTheSameDay() {
        LocalDate date = LocalDate.of(2026, 7, 19);
        List<AttendanceLog> logs = List.of(
                attendanceLog("CLOCK_IN", date.atTime(8, 0)),
                attendanceLog("CLOCK_OUT", date.atTime(10, 0)),
                attendanceLog("CLOCK_IN", date.atTime(14, 0))
        );

        HRService.AttendanceAggregate result = HRService.aggregateAttendance(
                date, logs, Timestamp.valueOf(date.atTime(15, 0)));

        assertEquals(2, result.sessionCount());
        assertEquals(180, result.workingMillis() / 60_000);
        assertEquals(Timestamp.valueOf(date.atTime(14, 0)), result.currentClockIn());
        assertTrue(result.open());
    }

    @Test
    void monthlyCalendarClassifiesShiftCoverageBreaksAbsenceAndAnnualLeave() {
        YearMonth month = YearMonth.from(LocalDate.now().minusMonths(1));
        LocalDate date = month.atDay(10);
        Shift shift = new Shift();
        shift.setId(31);
        shift.setShiftName("Standard Shift");
        shift.setStartTime(Time.valueOf("12:00:00"));
        shift.setEndTime(Time.valueOf("00:00:00"));

        List<Employee> employees = List.of(
                employee(1, 101, "Full"), employee(2, 102, "Below"),
                employee(3, 103, "Break"), employee(4, 104, "Absent"),
                employee(5, 105, "Leave")
        );
        List<EmployeeShift> assignments = employees.stream().map(employee -> new EmployeeShift(
                employee.getId(), COMPANY_ID, BRANCH_ID, employee.getId(), employee.getUserId(), 31,
                java.sql.Date.valueOf(month.atDay(1)), null, null, "system", null, "system"
        )).toList();
        List<AttendanceDay> records = List.of(
                attendanceDay(1, 101, date, 700, 20),
                attendanceDay(2, 102, date, 600, 20),
                attendanceDay(3, 103, date, 700, 61)
        );

        when(dbUsers.getUsersForCompany(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());
        when(dbHR.countActiveShifts(COMPANY_ID, BRANCH_ID)).thenReturn(1);
        when(dbHR.getAllEmployees(COMPANY_ID, BRANCH_ID)).thenReturn(employees);
        when(dbHR.getAttendanceReport(COMPANY_ID, BRANCH_ID, java.sql.Date.valueOf(month.atDay(1)), java.sql.Date.valueOf(month.atEndOfMonth())))
                .thenReturn(records);
        when(dbHR.getAssignmentsForPeriod(COMPANY_ID, BRANCH_ID, java.sql.Date.valueOf(month.atDay(1)), java.sql.Date.valueOf(month.atEndOfMonth())))
                .thenReturn(assignments);
        when(dbHR.getShiftsForScope(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(shift));
        when(dbHR.getApprovedAnnualLeaves(COMPANY_ID, BRANCH_ID, java.sql.Date.valueOf(month.atDay(1)), java.sql.Date.valueOf(month.atEndOfMonth())))
                .thenReturn(List.of(new AnnualLeavePeriod(105, BRANCH_ID, date, date)));

        AttendanceMonthResponse response = service.getAttendanceMonth(COMPANY_ID, BRANCH_ID, month, false, "sam");
        Map<Integer, String> statuses = response.days().get(9).employees().stream()
                .collect(Collectors.toMap(item -> item.userId(), item -> item.classification()));

        assertEquals("FULL_ATTENDANCE", statuses.get(101));
        assertEquals("BELOW_TARGET", statuses.get(102));
        assertEquals("EXCESSIVE_BREAK", statuses.get(103));
        assertEquals("ABSENT", statuses.get(104));
        assertEquals("ANNUAL_LEAVE", statuses.get(105));
    }

    private Employee employee(int employeeId, int userId, String firstName) {
        return new Employee(employeeId, COMPANY_ID, BRANCH_ID, String.valueOf(userId), "hash", firstName, "User",
                userId, true, null, "system", null, "system");
    }

    private AttendanceDay attendanceDay(int employeeId, int userId, LocalDate date, int workMinutes, int breakMinutes) {
        AttendanceDay day = new AttendanceDay();
        day.setCompanyId(COMPANY_ID);
        day.setBranchId(BRANCH_ID);
        day.setEmployeeId(employeeId);
        day.setUserId(userId);
        day.setAttendanceDate(java.sql.Date.valueOf(date));
        day.setClockIn(Timestamp.valueOf(date.atTime(12, 0)));
        day.setClockOut(Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        day.setWorkingMinutes(workMinutes);
        day.setBreakMinutes(breakMinutes);
        day.setStatus("PRESENT");
        return day;
    }

    private AttendanceLog attendanceLog(String actionType, java.time.LocalDateTime actionTime) {
        AttendanceLog log = new AttendanceLog();
        log.setActionType(actionType);
        log.setActionTime(Timestamp.valueOf(actionTime));
        return log;
    }
}
