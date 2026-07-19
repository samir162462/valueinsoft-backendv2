package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollSettingsService {

    private final DbPayroll dbPayroll;
    private final PayrollValidationService validationService;
    private final PayrollAuditService auditService;

    public PayrollSettingsService(DbPayroll dbPayroll,
                                  PayrollValidationService validationService,
                                  PayrollAuditService auditService) {
        this.dbPayroll = dbPayroll;
        this.validationService = validationService;
        this.auditService = auditService;
    }

    public PayrollSettings get(int companyId) {
        return dbPayroll.getSettings(companyId);
    }

    @Transactional
    public PayrollSettings update(String actor, PayrollSettings settings) {
        PayrollSettings existing = dbPayroll.getSettings(settings.getCompanyId());
        applyDefaults(settings, existing);
        validationService.validateSettingsAccounts(settings);
        if (existing == null) {
            settings.setCreatedBy(actor);
            settings.setUpdatedBy(actor);
            int id = dbPayroll.createSettings(settings);
            auditService.record(settings.getCompanyId(), null, "payroll_settings", String.valueOf(id),
                    "SETTINGS_CREATED", null, null, actor, "Payroll settings created");
            return dbPayroll.getSettings(settings.getCompanyId());
        }
        settings.setId(existing.getId());
        settings.setVersion(existing.getVersion());
        settings.setUpdatedBy(actor);
        int rows = dbPayroll.updateSettings(settings);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_SETTINGS_VERSION_CONFLICT",
                    "Payroll settings were modified by another request");
        }
        auditService.record(settings.getCompanyId(), null, "payroll_settings", String.valueOf(existing.getId()),
                "SETTINGS_UPDATED", null, null, actor, "Payroll settings updated");
        return dbPayroll.getSettings(settings.getCompanyId());
    }

    private void applyDefaults(PayrollSettings settings, PayrollSettings existing) {
        if (settings.getTimezoneId() == null || settings.getTimezoneId().isBlank()) {
            settings.setTimezoneId(existing == null || existing.getTimezoneId() == null ? "Africa/Cairo" : existing.getTimezoneId());
        }
        if (settings.getWeekStartDay() == null || settings.getWeekStartDay().isBlank()) {
            settings.setWeekStartDay(existing == null || existing.getWeekStartDay() == null ? "SUNDAY" : existing.getWeekStartDay());
        }
        if (settings.getWorkWeekDays() == null || settings.getWorkWeekDays().isBlank()) {
            settings.setWorkWeekDays(existing == null || existing.getWorkWeekDays() == null
                    ? "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY"
                    : existing.getWorkWeekDays());
        }
        if (settings.getMonthlyCutoffDay() < 1 || settings.getMonthlyCutoffDay() > 28) {
            settings.setMonthlyCutoffDay(existing == null || existing.getMonthlyCutoffDay() < 1 ? 25 : existing.getMonthlyCutoffDay());
        }
        if (settings.getDeductionPayableAccountId() == null && existing != null) {
            settings.setDeductionPayableAccountId(existing.getDeductionPayableAccountId());
        }
    }
}
