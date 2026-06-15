package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.HR.AttendanceDay;
import com.example.valueinsoftbackend.Model.Payroll.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PayrollCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DbPayroll dbPayroll;
    private final PayrollAttendanceIntegrationService attendanceIntegrationService;
    private final ObjectMapper objectMapper;

    public PayrollCalculationService(DbPayroll dbPayroll,
                                     PayrollAttendanceIntegrationService attendanceIntegrationService,
                                     ObjectMapper objectMapper) {
        this.dbPayroll = dbPayroll;
        this.attendanceIntegrationService = attendanceIntegrationService;
        this.objectMapper = objectMapper;
    }

    public List<PayrollRunLine> calculateRunLines(PayrollRun run, PayrollSettings settings) {
        List<PayrollSalaryProfile> profiles = dbPayroll.listSalaryProfiles(run.getCompanyId(), run.getBranchId(), null, true)
                .stream()
                .filter(profile -> overlaps(profile.getEffectiveFrom(), profile.getEffectiveTo(), run.getPeriodStart(), run.getPeriodEnd()))
                .toList();
        List<PayrollRunLine> lines = new ArrayList<>();
        for (PayrollSalaryProfile profile : profiles) {
            lines.add(calculateLine(run, settings, profile));
        }
        return lines;
    }

    public PayrollRunLine calculateLine(PayrollRun run, PayrollSettings settings, PayrollSalaryProfile profile) {
        BigDecimal base = scale(profile.getBaseSalary());
        BigDecimal allowances = BigDecimal.ZERO.setScale(4);
        BigDecimal deductions = BigDecimal.ZERO.setScale(4);
        List<PayrollRunLineComponent> componentSnapshots = new ArrayList<>();

        for (PayrollSalaryComponent component : dbPayroll.listSalaryComponents(run.getCompanyId(), profile.getId(), true)) {
            BigDecimal amount = componentAmount(base, component);
            if ("ALLOWANCE".equals(component.getComponentType())) {
                allowances = allowances.add(amount);
            } else {
                deductions = deductions.add(amount);
            }
            componentSnapshots.add(snapshotComponent(component, amount, "PROFILE"));
        }

        List<PayrollAdjustment> adjustments = dbPayroll.listAdjustments(run.getCompanyId(), run.getBranchId(), profile.getEmployeeId(), "APPROVED")
                .stream()
                .filter(adjustment -> !adjustment.getEffectiveDate().before(run.getPeriodStart()) && !adjustment.getEffectiveDate().after(run.getPeriodEnd()))
                .toList();
        for (PayrollAdjustment adjustment : adjustments) {
            BigDecimal amount = scale(adjustment.getAmount());
            if ("ALLOWANCE".equals(adjustment.getAdjustmentType())) {
                allowances = allowances.add(amount);
            } else {
                deductions = deductions.add(amount);
            }
            componentSnapshots.add(adjustmentComponent(adjustment, amount));
        }

        AttendanceTotals attendanceTotals = attendanceTotals(run, settings, profile);
        BigDecimal attendanceDeduction = scale(settings == null ? BigDecimal.ZERO
                : settings.getLateDeductionPerMinute().multiply(BigDecimal.valueOf(attendanceTotals.lateMinutes())));
        BigDecimal absentDeduction = scale(base.divide(BigDecimal.valueOf(22), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(attendanceTotals.absentDays())));
        attendanceDeduction = attendanceDeduction.add(absentDeduction).setScale(4, RoundingMode.HALF_UP);
        if (attendanceDeduction.compareTo(BigDecimal.ZERO) > 0) {
            deductions = deductions.add(attendanceDeduction);
            componentSnapshots.add(attendanceComponent(attendanceDeduction));
        }
        BigDecimal overtimeAllowance = overtimeAllowance(base, settings, attendanceTotals.overtimeMinutes());
        if (overtimeAllowance.compareTo(BigDecimal.ZERO) > 0) {
            allowances = allowances.add(overtimeAllowance);
            componentSnapshots.add(overtimeComponent(overtimeAllowance));
        }

        BigDecimal gross = base.add(allowances).setScale(4, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(deductions).setScale(4, RoundingMode.HALF_UP);

        PayrollRunLine line = new PayrollRunLine();
        line.setCompanyId(run.getCompanyId());
        line.setPayrollRunId(run.getId());
        line.setEmployeeId(profile.getEmployeeId());
        line.setSalaryProfileId(profile.getId());
        line.setBaseSalary(base);
        line.setTotalAllowances(allowances);
        line.setTotalDeductions(deductions);
        line.setGrossSalary(gross);
        line.setNetSalary(net);
        line.setPaidAmount(BigDecimal.ZERO.setScale(4));
        line.setRemainingAmount(net);
        line.setPaymentStatus("UNPAID");
        line.setWorkingDays(attendanceTotals.workingDays());
        line.setAbsentDays(attendanceTotals.absentDays());
        line.setLateMinutes(attendanceTotals.lateMinutes());
        line.setOvertimeMinutes(attendanceTotals.overtimeMinutes());
        line.setSalaryType(profile.getSalaryType());
        line.setPayrollFrequency(profile.getPayrollFrequency());
        line.setCurrencyCode(profile.getCurrencyCode());
        line.setCalculationSnapshotJson(snapshotJson(profile, componentSnapshots, adjustments, attendanceTotals));
        return line;
    }

    private AttendanceTotals attendanceTotals(PayrollRun run, PayrollSettings settings, PayrollSalaryProfile profile) {
        if (settings != null && !settings.isAutoIncludeAttendance()) {
            return new AttendanceTotals(0, 0, 0, 0);
        }
        List<AttendanceDay> days = attendanceIntegrationService.getAttendanceForPeriod(
                run.getCompanyId(), profile.getEmployeeId(), run.getPeriodStart(), run.getPeriodEnd());
        int working = days.size();
        int absent = (int) days.stream().filter(day -> "ABSENT".equalsIgnoreCase(day.getStatus())).count();
        int late = days.stream().mapToInt(AttendanceDay::getLateMinutes).sum();
        int overtime = days.stream().mapToInt(AttendanceDay::getOvertimeMinutes).sum();
        return new AttendanceTotals(working, absent, late, overtime);
    }

    private boolean overlaps(Date existingFrom, Date existingTo, Date periodStart, Date periodEnd) {
        return !existingFrom.after(periodEnd) && (existingTo == null || !existingTo.before(periodStart));
    }

    private PayrollRunLineComponent snapshotComponent(PayrollSalaryComponent component, BigDecimal amount, String source) {
        PayrollRunLineComponent snapshot = new PayrollRunLineComponent();
        snapshot.setCompanyId(component.getCompanyId());
        snapshot.setComponentType(component.getComponentType());
        snapshot.setTypeId("ALLOWANCE".equals(component.getComponentType()) ? component.getAllowanceTypeId() : component.getDeductionTypeId());
        snapshot.setCalcMethod(component.getCalcMethod());
        snapshot.setAmount(amount);
        snapshot.setSource(source);
        return snapshot;
    }

    private PayrollRunLineComponent adjustmentComponent(PayrollAdjustment adjustment, BigDecimal amount) {
        PayrollRunLineComponent component = new PayrollRunLineComponent();
        component.setCompanyId(adjustment.getCompanyId());
        component.setComponentType(adjustment.getAdjustmentType());
        component.setTypeCode(adjustment.getAdjustmentCode());
        component.setTypeName(adjustment.getDescription());
        component.setCalcMethod("FIXED");
        component.setAmount(amount);
        component.setSource("ADJUSTMENT");
        return component;
    }

    private PayrollRunLineComponent attendanceComponent(BigDecimal amount) {
        PayrollRunLineComponent component = new PayrollRunLineComponent();
        component.setComponentType("DEDUCTION");
        component.setTypeCode("ATTENDANCE");
        component.setTypeName("Attendance deduction");
        component.setCalcMethod("FIXED");
        component.setAmount(amount);
        component.setSource("ATTENDANCE");
        return component;
    }

    private PayrollRunLineComponent overtimeComponent(BigDecimal amount) {
        PayrollRunLineComponent component = new PayrollRunLineComponent();
        component.setComponentType("ALLOWANCE");
        component.setTypeCode("OVERTIME");
        component.setTypeName("Overtime allowance");
        component.setCalcMethod("FIXED");
        component.setAmount(amount);
        component.setSource("ATTENDANCE");
        return component;
    }

    private BigDecimal componentAmount(BigDecimal base, PayrollSalaryComponent component) {
        if ("PERCENTAGE".equals(component.getCalcMethod())) {
            return scale(base.multiply(component.getPercentage()).divide(HUNDRED, 4, RoundingMode.HALF_UP));
        }
        return scale(component.getAmount());
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal overtimeAllowance(BigDecimal base, PayrollSettings settings, int overtimeMinutes) {
        if (settings == null || overtimeMinutes <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        BigDecimal minuteRate = base
                .divide(BigDecimal.valueOf(22), 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(8), 8, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
        return scale(minuteRate
                .multiply(BigDecimal.valueOf(overtimeMinutes))
                .multiply(settings.getOvertimeRateMultiplier()));
    }

    private String snapshotJson(PayrollSalaryProfile profile,
                                List<PayrollRunLineComponent> components,
                                List<PayrollAdjustment> adjustments,
                                AttendanceTotals attendanceTotals) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "salaryProfileId", profile.getId(),
                    "baseSalary", profile.getBaseSalary(),
                    "salaryType", profile.getSalaryType(),
                    "payrollFrequency", profile.getPayrollFrequency(),
                    "components", components,
                    "adjustments", adjustments,
                    "attendance", attendanceTotals));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public record AttendanceTotals(int workingDays, int absentDays, int lateMinutes, int overtimeMinutes) {
    }
}
