package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollSettingsService {

    private final DbPayroll dbPayroll;
    private final PayrollAuditService auditService;

    public PayrollSettingsService(DbPayroll dbPayroll, PayrollAuditService auditService) {
        this.dbPayroll = dbPayroll;
        this.auditService = auditService;
    }

    public PayrollSettings get(int companyId) {
        return dbPayroll.getSettings(companyId);
    }

    @Transactional
    public PayrollSettings update(String actor, PayrollSettings settings) {
        PayrollSettings existing = dbPayroll.getSettings(settings.getCompanyId());
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
}
