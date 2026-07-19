package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAdjustment;
import com.example.valueinsoftbackend.Model.Payroll.PayrollAttendanceSnapshot;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRunLine;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRunLineComponent;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryComponent;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayrollCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal SIXTY = new BigDecimal("60");

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

    public List<CalculatedPayrollLine> calculateRun(PayrollRun run, PayrollSettings settings) {
        Map<Integer, PayrollSalaryProfile> latestProfileByUser = new LinkedHashMap<>();
        dbPayroll.listSalaryProfiles(run.getCompanyId(), run.getBranchId(), null, true).stream()
                .filter(profile -> profile.getUserId() != null)
                .filter(profile -> run.getFrequency().equalsIgnoreCase(profile.getPayrollFrequency()))
                .filter(profile -> overlaps(profile.getEffectiveFrom(), profile.getEffectiveTo(), run.getPeriodStart(), run.getPeriodEnd()))
                .sorted(Comparator.comparing(PayrollSalaryProfile::getEffectiveFrom))
                .forEach(profile -> latestProfileByUser.put(profile.getUserId(), profile));

        List<CalculatedPayrollLine> results = new ArrayList<>();
        for (PayrollSalaryProfile profile : latestProfileByUser.values()) {
            results.add(calculateLine(run, settings, profile));
        }
        return results;
    }

    public CalculatedPayrollLine calculateLine(PayrollRun run, PayrollSettings settings, PayrollSalaryProfile profile) {
        if (profile.getUserId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_PROFILE_USER_ID_MISSING",
                    "Salary profile is not linked to an authoritative company user");
        }

        List<PayrollAttendanceSnapshot> attendance = settings != null && !settings.isAutoIncludeAttendance()
                ? List.of()
                : attendanceIntegrationService.getAttendanceForPeriod(
                        run.getCompanyId(), profile.getBranchId(), profile.getEmployeeId(), profile.getUserId(),
                        run.getPeriodStart(), run.getPeriodEnd(), settings);
        AttendanceTotals attendanceTotals = attendanceTotals(attendance);
        BigDecimal base = basePay(profile, attendanceTotals);
        BigDecimal allowances = BigDecimal.ZERO.setScale(4);
        BigDecimal wageReductions = BigDecimal.ZERO.setScale(4);
        BigDecimal withholdings = BigDecimal.ZERO.setScale(4);
        List<PayrollRunLineComponent> componentSnapshots = new ArrayList<>();

        for (PayrollSalaryComponent component : dbPayroll.listSalaryComponents(run.getCompanyId(), profile.getId(), true)) {
            BigDecimal amount = componentAmount(base, component);
            if ("ALLOWANCE".equals(component.getComponentType())) {
                allowances = allowances.add(amount);
            } else {
                withholdings = withholdings.add(amount);
            }
            componentSnapshots.add(snapshotComponent(component, amount, "PROFILE"));
        }

        List<PayrollAdjustment> adjustments = dbPayroll.listAdjustments(run.getCompanyId(), run.getBranchId(), profile.getEmployeeId(), "APPROVED")
                .stream()
                .filter(adjustment -> profile.getUserId().equals(adjustment.getUserId()))
                .filter(adjustment -> !adjustment.getEffectiveDate().before(run.getPeriodStart()) && !adjustment.getEffectiveDate().after(run.getPeriodEnd()))
                .toList();
        for (PayrollAdjustment adjustment : adjustments) {
            BigDecimal amount = scale(adjustment.getAmount());
            if ("ALLOWANCE".equals(adjustment.getAdjustmentType())) {
                allowances = allowances.add(amount);
            } else {
                wageReductions = wageReductions.add(amount);
            }
            componentSnapshots.add(adjustmentComponent(adjustment, amount));
        }

        BigDecimal attendanceReduction = attendanceReduction(base, settings, attendanceTotals, profile.getSalaryType());
        if (attendanceReduction.signum() > 0) {
            wageReductions = wageReductions.add(attendanceReduction);
            componentSnapshots.add(attendanceComponent(attendanceReduction));
        }
        BigDecimal overtimeAllowance = overtimeAllowance(base, settings, attendanceTotals);
        if (overtimeAllowance.signum() > 0) {
            allowances = allowances.add(overtimeAllowance);
            componentSnapshots.add(overtimeComponent(overtimeAllowance));
        }

        BigDecimal gross = base.add(allowances).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalDeductions = wageReductions.add(withholdings).setScale(4, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(totalDeductions).setScale(4, RoundingMode.HALF_UP);
        if (net.signum() < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_NEGATIVE_NET_SALARY",
                    "Payroll deductions exceed earnings for user " + profile.getUserId());
        }

        PayrollRunLine line = new PayrollRunLine();
        line.setCompanyId(run.getCompanyId());
        line.setPayrollRunId(run.getId());
        line.setEmployeeId(profile.getEmployeeId());
        line.setUserId(profile.getUserId());
        line.setSalaryProfileId(profile.getId());
        line.setBaseSalary(base);
        line.setTotalAllowances(allowances);
        line.setTotalDeductions(totalDeductions);
        line.setWageReductionTotal(wageReductions);
        line.setWithholdingTotal(withholdings);
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
        line.setCalculationSnapshotJson(snapshotJson(profile, componentSnapshots, attendanceTotals));
        return new CalculatedPayrollLine(line, componentSnapshots, attendance);
    }

    private AttendanceTotals attendanceTotals(List<PayrollAttendanceSnapshot> days) {
        int working = (int) days.stream().filter(day -> "PRESENT".equals(day.getDayStatus()) || "PAID_LEAVE".equals(day.getDayStatus())).count();
        int absent = (int) days.stream().filter(day -> "ABSENT".equals(day.getDayStatus())).count();
        int scheduled = days.stream().mapToInt(PayrollAttendanceSnapshot::getScheduledMinutes).sum();
        int absentScheduled = days.stream().filter(day -> "ABSENT".equals(day.getDayStatus())).mapToInt(PayrollAttendanceSnapshot::getScheduledMinutes).sum();
        int payable = days.stream().mapToInt(PayrollAttendanceSnapshot::getPayableMinutes).sum();
        int late = days.stream().mapToInt(PayrollAttendanceSnapshot::getLateMinutes).sum();
        int overtime = days.stream().mapToInt(PayrollAttendanceSnapshot::getOvertimeMinutes).sum();
        return new AttendanceTotals(working, absent, scheduled, absentScheduled, payable, late, overtime);
    }

    private BigDecimal basePay(PayrollSalaryProfile profile, AttendanceTotals totals) {
        BigDecimal configured = scale(profile.getBaseSalary());
        if ("DAILY".equals(profile.getSalaryType())) {
            return scale(configured.multiply(BigDecimal.valueOf(totals.workingDays())));
        }
        if ("HOURLY".equals(profile.getSalaryType()) || "FLEXIBLE".equals(profile.getSalaryType())) {
            int ordinaryPayable = Math.max(0, totals.payableMinutes() - totals.overtimeMinutes());
            return scale(configured.multiply(BigDecimal.valueOf(ordinaryPayable)).divide(SIXTY, 4, RoundingMode.HALF_UP));
        }
        return configured;
    }

    private BigDecimal attendanceReduction(BigDecimal base,
                                           PayrollSettings settings,
                                           AttendanceTotals totals,
                                           String salaryType) {
        if (settings == null || "DAILY".equals(salaryType) || "HOURLY".equals(salaryType) || "FLEXIBLE".equals(salaryType)) {
            return BigDecimal.ZERO.setScale(4);
        }
        BigDecimal absent = totals.scheduledMinutes() <= 0
                ? BigDecimal.ZERO
                : base.multiply(BigDecimal.valueOf(totals.absentScheduledMinutes()))
                        .divide(BigDecimal.valueOf(totals.scheduledMinutes()), 4, RoundingMode.HALF_UP);
        BigDecimal late = scale(settings.getLateDeductionPerMinute())
                .multiply(BigDecimal.valueOf(totals.lateMinutes()));
        return scale(absent.add(late).min(base));
    }

    private BigDecimal overtimeAllowance(BigDecimal base, PayrollSettings settings, AttendanceTotals totals) {
        if (settings == null || totals.overtimeMinutes() <= 0 || totals.scheduledMinutes() <= 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        BigDecimal minuteRate = base.divide(BigDecimal.valueOf(totals.scheduledMinutes()), 8, RoundingMode.HALF_UP);
        return scale(minuteRate.multiply(BigDecimal.valueOf(totals.overtimeMinutes()))
                .multiply(settings.getOvertimeRateMultiplier()));
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
        component.setTypeCode("ATTENDANCE_WAGE_REDUCTION");
        component.setTypeName("Attendance wage reduction");
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

    private String snapshotJson(PayrollSalaryProfile profile,
                                List<PayrollRunLineComponent> components,
                                AttendanceTotals attendanceTotals) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("userId", profile.getUserId());
            snapshot.put("employeeId", profile.getEmployeeId());
            snapshot.put("salaryProfileId", profile.getId());
            snapshot.put("baseSalary", profile.getBaseSalary());
            snapshot.put("salaryType", profile.getSalaryType());
            snapshot.put("payrollFrequency", profile.getPayrollFrequency());
            snapshot.put("components", components);
            snapshot.put("attendance", attendanceTotals);
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYROLL_SNAPSHOT_SERIALIZATION_FAILED",
                    "Payroll calculation snapshot could not be serialized");
        }
    }

    public record CalculatedPayrollLine(PayrollRunLine line,
                                        List<PayrollRunLineComponent> components,
                                        List<PayrollAttendanceSnapshot> attendanceSnapshots) {
    }

    public record AttendanceTotals(int workingDays,
                                   int absentDays,
                                   int scheduledMinutes,
                                   int absentScheduledMinutes,
                                   int payableMinutes,
                                   int lateMinutes,
                                   int overtimeMinutes) {
    }
}
