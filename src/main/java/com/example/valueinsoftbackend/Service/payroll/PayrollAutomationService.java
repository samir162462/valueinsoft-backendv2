package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PayrollAutomationService {

    private final DbPayroll dbPayroll;
    private final PayrollSettingsService settingsService;
    private final PayrollRunService runService;

    public PayrollAutomationService(DbPayroll dbPayroll,
                                    PayrollSettingsService settingsService,
                                    PayrollRunService runService) {
        this.dbPayroll = dbPayroll;
        this.settingsService = settingsService;
        this.runService = runService;
    }

    public List<PayrollRun> ensureCurrentRuns(String actor, int companyId, Integer branchId) {
        PayrollSettings settings = settingsService.get(companyId);
        String timezone = settings == null || settings.getTimezoneId() == null ? "Africa/Cairo" : settings.getTimezoneId();
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(timezone));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_TIMEZONE_INVALID", "Payroll timezone is invalid");
        }

        Map<String, PayrollSalaryProfile> scopes = new LinkedHashMap<>();
        for (PayrollSalaryProfile profile : dbPayroll.listSalaryProfiles(companyId, branchId, null, true)) {
            if (profile.getUserId() == null || profile.getEffectiveFrom().after(Date.valueOf(today))
                    || (profile.getEffectiveTo() != null && profile.getEffectiveTo().before(Date.valueOf(today)))) {
                continue;
            }
            String frequency = normalize(profile.getPayrollFrequency(), settings == null ? "MONTHLY" : settings.getDefaultFrequency());
            String currency = normalize(profile.getCurrencyCode(), settings == null ? "EGP" : settings.getDefaultCurrency());
            scopes.putIfAbsent(frequency + "|" + currency, profile);
        }

        List<PayrollRun> runs = new ArrayList<>();
        for (Map.Entry<String, PayrollSalaryProfile> entry : scopes.entrySet()) {
            String[] scope = entry.getKey().split("\\|", 2);
            PayrollPeriod period = currentPeriod(scope[0], today, settings);
            PayrollRun request = new PayrollRun();
            request.setCompanyId(companyId);
            request.setBranchId(branchId);
            request.setFrequency(scope[0]);
            request.setCurrencyCode(scope[1]);
            request.setPeriodStart(Date.valueOf(period.start()));
            request.setPeriodEnd(Date.valueOf(period.end()));
            request.setRunLabel(scope[0] + " payroll " + period.start() + " to " + period.end());
            runs.add(runService.generate(actor, request));
        }
        return runs;
    }

    private PayrollPeriod currentPeriod(String frequency, LocalDate today, PayrollSettings settings) {
        if ("MONTHLY".equals(frequency)) {
            return new PayrollPeriod(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()));
        }
        DayOfWeek weekStart;
        try {
            weekStart = DayOfWeek.valueOf(normalize(settings == null ? null : settings.getWeekStartDay(), "SUNDAY"));
        } catch (IllegalArgumentException exception) {
            weekStart = DayOfWeek.SUNDAY;
        }
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(weekStart));
        if ("WEEKLY".equals(frequency)) {
            return new PayrollPeriod(start, start.plusDays(6));
        }
        if ("BIWEEKLY".equals(frequency)) {
            long weekIndex = java.time.temporal.ChronoUnit.WEEKS.between(LocalDate.of(1970, 1, 4), start);
            if (Math.floorMod(weekIndex, 2) != 0) {
                start = start.minusWeeks(1);
            }
            return new PayrollPeriod(start, start.plusDays(13));
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_CUSTOM_PERIOD_REQUIRED",
                "Custom payroll frequency requires an explicit off-cycle payroll period");
    }

    private String normalize(String value, String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value;
        return selected == null ? "" : selected.trim().toUpperCase(Locale.ROOT);
    }

    private record PayrollPeriod(LocalDate start, LocalDate end) {
    }
}
