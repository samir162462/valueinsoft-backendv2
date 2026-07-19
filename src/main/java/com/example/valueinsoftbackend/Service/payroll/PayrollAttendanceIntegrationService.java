package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.Model.HR.AnnualLeavePeriod;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import com.example.valueinsoftbackend.Model.HR.Shift;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAttendanceSnapshot;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PayrollAttendanceIntegrationService {

    private static final String DEFAULT_WORK_WEEK = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY";

    private final DbHR dbHR;

    public PayrollAttendanceIntegrationService(DbHR dbHR) {
        this.dbHR = dbHR;
    }

    public List<PayrollAttendanceSnapshot> getAttendanceForPeriod(int companyId,
                                                                   int branchId,
                                                                   int employeeId,
                                                                   int userId,
                                                                   Date start,
                                                                   Date end,
                                                                   PayrollSettings settings) {
        Map<LocalDate, AttendanceDay> attendanceByDate = new HashMap<>();
        for (AttendanceDay day : dbHR.getAttendanceForUserPeriod(companyId, userId, start, end)) {
            attendanceByDate.put(day.getAttendanceDate().toLocalDate(), day);
        }
        List<AnnualLeavePeriod> leaves = dbHR.getApprovedAnnualLeaves(companyId, branchId, start, end).stream()
                .filter(leave -> leave.userId() == userId)
                .toList();
        Set<String> workWeek = workWeek(settings);
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(settings == null || settings.getTimezoneId() == null
                    ? "Africa/Cairo" : settings.getTimezoneId()));
        } catch (Exception exception) {
            today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        }

        LocalDate payrollToday = today;
        return start.toLocalDate().datesUntil(end.toLocalDate().plusDays(1))
                .map(date -> snapshotDay(companyId, branchId, employeeId, userId, date,
                        attendanceByDate.get(date), leaves, workWeek, payrollToday))
                .toList();
    }

    private PayrollAttendanceSnapshot snapshotDay(int companyId,
                                                   int branchId,
                                                   int employeeId,
                                                   int userId,
                                                   LocalDate date,
                                                   AttendanceDay attendance,
                                                   List<AnnualLeavePeriod> leaves,
                                                   Set<String> workWeek,
                                                   LocalDate today) {
        Shift shift = dbHR.getActiveShiftForUser(companyId, userId, Date.valueOf(date));
        boolean scheduledWorkDay = shift != null && workWeek.contains(date.getDayOfWeek().name());
        int scheduledMinutes = scheduledWorkDay ? shiftMinutes(shift) : 0;
        boolean paidLeave = scheduledWorkDay && leaves.stream().anyMatch(leave ->
                !date.isBefore(leave.startDate()) && !date.isAfter(leave.endDate()));
        int workedMinutes = attendance == null ? 0 : Math.max(0, attendance.getWorkingMinutes());
        int breakMinutes = attendance == null ? 0 : Math.max(0, attendance.getBreakMinutes());
        int lateMinutes = attendance == null || !scheduledWorkDay ? 0 : Math.max(0, attendance.getLateMinutes());
        int overtimeMinutes = attendance == null ? 0 : Math.max(0, attendance.getOvertimeMinutes());
        int payableMinutes = paidLeave
                ? scheduledMinutes
                : Math.min(scheduledMinutes, workedMinutes) + overtimeMinutes;
        String status;
        if (date.isAfter(today)) {
            status = "FUTURE";
            paidLeave = false;
            payableMinutes = 0;
        } else if (paidLeave) {
            status = "PAID_LEAVE";
        } else if (!scheduledWorkDay) {
            status = attendance == null ? "REST_DAY" : "UNSCHEDULED_WORK";
        } else if (attendance == null || workedMinutes == 0 || "ABSENT".equalsIgnoreCase(attendance.getStatus())) {
            status = "ABSENT";
        } else if (attendance.getClockOut() == null) {
            status = "OPEN_ATTENDANCE";
        } else {
            status = "PRESENT";
        }

        PayrollAttendanceSnapshot snapshot = new PayrollAttendanceSnapshot();
        snapshot.setCompanyId(companyId);
        snapshot.setEmployeeId(employeeId);
        snapshot.setUserId(userId);
        snapshot.setBranchId(branchId);
        snapshot.setAttendanceDate(Date.valueOf(date));
        snapshot.setShiftId(shift == null ? null : shift.getId());
        snapshot.setScheduledMinutes(scheduledMinutes);
        snapshot.setWorkedMinutes(workedMinutes);
        snapshot.setBreakMinutes(breakMinutes);
        snapshot.setLateMinutes(lateMinutes);
        snapshot.setOvertimeMinutes(overtimeMinutes);
        snapshot.setPayableMinutes(Math.max(0, payableMinutes));
        snapshot.setDayStatus(status);
        snapshot.setPaidLeave(paidLeave);
        snapshot.setSourceAttendanceDayId(attendance == null ? null : attendance.getId());
        return snapshot;
    }

    private int shiftMinutes(Shift shift) {
        LocalTime start = shift.getStartTime().toLocalTime();
        LocalTime end = shift.getEndTime().toLocalTime();
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            minutes += Duration.ofDays(1).toMinutes();
        }
        return Math.toIntExact(minutes);
    }

    private Set<String> workWeek(PayrollSettings settings) {
        String configured = settings == null || settings.getWorkWeekDays() == null
                ? DEFAULT_WORK_WEEK
                : settings.getWorkWeekDays();
        Set<String> days = new HashSet<>();
        for (String day : configured.split(",")) {
            String normalized = day.trim().toUpperCase();
            if (!normalized.isBlank()) {
                days.add(normalized);
            }
        }
        return days.isEmpty() ? Set.of(DEFAULT_WORK_WEEK.split(",")) : days;
    }
}
