package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.Model.HR.AnnualLeavePeriod;
import com.example.valueinsoftbackend.Model.HR.Shift;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAttendanceSnapshot;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class PayrollAttendanceIntegrationServiceTest {

    private DbHR dbHR;
    private PayrollAttendanceIntegrationService service;

    @BeforeEach
    void setUp() {
        dbHR = Mockito.mock(DbHR.class);
        service = new PayrollAttendanceIntegrationService(dbHR);
    }

    @Test
    void missingAttendanceOnScheduledDayBecomesAbsentByUserId() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        Shift shift = shift();
        when(dbHR.getAttendanceForUserPeriod(1095, 42, Date.valueOf(today), Date.valueOf(today))).thenReturn(List.of());
        when(dbHR.getApprovedAnnualLeaves(1095, 7, Date.valueOf(today), Date.valueOf(today))).thenReturn(List.of());
        when(dbHR.getActiveShiftForUser(1095, 42, Date.valueOf(today))).thenReturn(shift);

        List<PayrollAttendanceSnapshot> result = service.getAttendanceForPeriod(
                1095, 7, 11, 42, Date.valueOf(today), Date.valueOf(today), settings(today));

        assertEquals(1, result.size());
        assertEquals("ABSENT", result.get(0).getDayStatus());
        assertEquals(720, result.get(0).getScheduledMinutes());
        assertEquals(42, result.get(0).getUserId());
    }

    @Test
    void approvedAnnualLeaveIsPaidInsteadOfAbsent() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        when(dbHR.getAttendanceForUserPeriod(1095, 42, Date.valueOf(today), Date.valueOf(today))).thenReturn(List.of());
        when(dbHR.getApprovedAnnualLeaves(1095, 7, Date.valueOf(today), Date.valueOf(today)))
                .thenReturn(List.of(new AnnualLeavePeriod(42, 7, today, today)));
        when(dbHR.getActiveShiftForUser(1095, 42, Date.valueOf(today))).thenReturn(shift());

        PayrollAttendanceSnapshot result = service.getAttendanceForPeriod(
                1095, 7, 11, 42, Date.valueOf(today), Date.valueOf(today), settings(today)).get(0);

        assertEquals("PAID_LEAVE", result.getDayStatus());
        assertTrue(result.isPaidLeave());
        assertEquals(result.getScheduledMinutes(), result.getPayableMinutes());
    }

    private PayrollSettings settings(LocalDate date) {
        PayrollSettings settings = new PayrollSettings();
        settings.setTimezoneId("Africa/Cairo");
        settings.setWorkWeekDays(date.getDayOfWeek().name());
        return settings;
    }

    private Shift shift() {
        Shift shift = new Shift();
        shift.setId(31);
        shift.setStartTime(Time.valueOf("12:00:00"));
        shift.setEndTime(Time.valueOf("00:00:00"));
        shift.setActive(true);
        return shift;
    }
}
