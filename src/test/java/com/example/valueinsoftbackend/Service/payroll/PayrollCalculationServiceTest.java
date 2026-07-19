package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAttendanceSnapshot;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PayrollCalculationServiceTest {

    @Test
    void calculationUsesUserAttendanceSnapshotAndSeparatesWageReduction() {
        DbPayroll dbPayroll = Mockito.mock(DbPayroll.class);
        PayrollAttendanceIntegrationService attendance = Mockito.mock(PayrollAttendanceIntegrationService.class);
        PayrollCalculationService service = new PayrollCalculationService(dbPayroll, attendance, new ObjectMapper());
        PayrollRun run = run();
        PayrollSalaryProfile profile = profile();
        PayrollSettings settings = settings();

        when(dbPayroll.listSalaryProfiles(1095, 7, null, true)).thenReturn(List.of(profile));
        when(dbPayroll.listSalaryComponents(1095, 91, true)).thenReturn(List.of());
        when(dbPayroll.listAdjustments(1095, 7, 11, "APPROVED")).thenReturn(List.of());
        when(attendance.getAttendanceForPeriod(any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class), any(), any(), any()))
                .thenReturn(List.of(day("PRESENT", 480), day("ABSENT", 0)));

        var result = service.calculateRun(run, settings).get(0).line();

        assertEquals(42, result.getUserId());
        assertEquals(new BigDecimal("1000.0000"), result.getWageReductionTotal());
        assertEquals(new BigDecimal("0.0000"), result.getWithholdingTotal());
        assertEquals(new BigDecimal("1000.0000"), result.getNetSalary());
        assertEquals(1, result.getAbsentDays());
    }

    private PayrollRun run() {
        PayrollRun run = new PayrollRun();
        run.setId(5);
        run.setCompanyId(1095);
        run.setBranchId(7);
        run.setFrequency("MONTHLY");
        run.setPeriodStart(Date.valueOf(LocalDate.now().minusDays(1)));
        run.setPeriodEnd(Date.valueOf(LocalDate.now()));
        return run;
    }

    private PayrollSalaryProfile profile() {
        PayrollSalaryProfile profile = new PayrollSalaryProfile();
        profile.setId(91);
        profile.setCompanyId(1095);
        profile.setEmployeeId(11);
        profile.setUserId(42);
        profile.setBranchId(7);
        profile.setSalaryType("MONTHLY");
        profile.setPayrollFrequency("MONTHLY");
        profile.setCurrencyCode("EGP");
        profile.setBaseSalary(new BigDecimal("2000"));
        profile.setEffectiveFrom(Date.valueOf(LocalDate.now().minusMonths(1)));
        profile.setActive(true);
        return profile;
    }

    private PayrollSettings settings() {
        PayrollSettings settings = new PayrollSettings();
        settings.setAutoIncludeAttendance(true);
        settings.setLateDeductionPerMinute(BigDecimal.ZERO);
        settings.setOvertimeRateMultiplier(new BigDecimal("1.5"));
        return settings;
    }

    private PayrollAttendanceSnapshot day(String status, int workedMinutes) {
        PayrollAttendanceSnapshot day = new PayrollAttendanceSnapshot();
        day.setDayStatus(status);
        day.setScheduledMinutes(480);
        day.setWorkedMinutes(workedMinutes);
        day.setPayableMinutes(workedMinutes);
        return day;
    }
}
