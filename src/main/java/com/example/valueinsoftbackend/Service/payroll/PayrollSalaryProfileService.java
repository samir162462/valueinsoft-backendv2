package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.DatabaseRequests.DbHR;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.HR.Employee;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryComponent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PayrollSalaryProfileService {

    private final DbPayroll dbPayroll;
    private final DbHR dbHR;
    private final PayrollValidationService validationService;
    private final PayrollAuditService auditService;

    public PayrollSalaryProfileService(DbPayroll dbPayroll,
                                       DbHR dbHR,
                                       PayrollValidationService validationService,
                                       PayrollAuditService auditService) {
        this.dbPayroll = dbPayroll;
        this.dbHR = dbHR;
        this.validationService = validationService;
        this.auditService = auditService;
    }

    public List<PayrollSalaryProfile> listByCompany(int companyId, Integer branchId, Integer employeeId, Boolean activeOnly) {
        return dbPayroll.listSalaryProfiles(companyId, branchId, employeeId, activeOnly);
    }

    public PayrollSalaryProfile get(int companyId, long id) {
        PayrollSalaryProfile profile = dbPayroll.getSalaryProfile(companyId, id);
        if (profile == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYROLL_PROFILE_NOT_FOUND", "Salary profile was not found");
        }
        return profile;
    }

    public PayrollSalaryProfile getByEmployee(int companyId, int employeeId) {
        return dbPayroll.listSalaryProfiles(companyId, null, employeeId, true).stream().findFirst().orElse(null);
    }

    public PayrollSalaryProfile getByUser(int companyId, int userId) {
        return dbPayroll.listSalaryProfiles(companyId, null, null, true).stream()
                .filter(profile -> profile.getUserId() != null && profile.getUserId() == userId)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public PayrollSalaryProfile create(String actor, PayrollSalaryProfile profile) {
        resolveAuthoritativeIdentity(profile);
        dbPayroll.lockPayrollUser(profile.getCompanyId(), profile.getUserId());
        applyDefaults(profile);
        validationService.validateNoOverlap(profile, null);
        validationService.validateAccounts(profile.getCompanyId(), profile, dbPayroll.getSettings(profile.getCompanyId()));
        profile.setCreatedBy(actor);
        profile.setUpdatedBy(actor);
        long id = dbPayroll.createSalaryProfile(profile);
        auditService.record(profile.getCompanyId(), profile.getBranchId(), "salary_profile", String.valueOf(id),
                "PROFILE_CREATED", null, null, actor, "Salary profile created");
        return get(profile.getCompanyId(), id);
    }

    @Transactional
    public PayrollSalaryProfile update(String actor, long id, PayrollSalaryProfile profile) {
        PayrollSalaryProfile existing = get(profile.getCompanyId(), id);
        profile.setId(id);
        profile.setEmployeeId(existing.getEmployeeId());
        profile.setUserId(existing.getUserId());
        profile.setBranchId(existing.getBranchId());
        dbPayroll.lockPayrollUser(profile.getCompanyId(), profile.getUserId());
        profile.setVersion(existing.getVersion());
        applyDefaults(profile);
        validationService.validateNoOverlap(profile, id);
        validationService.validateAccounts(profile.getCompanyId(), profile, dbPayroll.getSettings(profile.getCompanyId()));
        profile.setUpdatedBy(actor);
        int rows = dbPayroll.updateSalaryProfile(profile);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_PROFILE_VERSION_CONFLICT",
                    "Salary profile was modified by another request");
        }
        auditService.record(profile.getCompanyId(), profile.getBranchId(), "salary_profile", String.valueOf(id),
                "PROFILE_UPDATED", null, null, actor, "Salary profile updated");
        return get(profile.getCompanyId(), id);
    }

    @Transactional
    public PayrollSalaryProfile deactivate(String actor, int companyId, long id) {
        PayrollSalaryProfile existing = get(companyId, id);
        dbPayroll.deactivateSalaryProfile(companyId, id, actor);
        auditService.record(companyId, existing.getBranchId(), "salary_profile", String.valueOf(id),
                "PROFILE_DEACTIVATED", null, null, actor, "Salary profile deactivated");
        return get(companyId, id);
    }

    public List<PayrollSalaryComponent> listComponents(int companyId, long salaryProfileId, Boolean activeOnly) {
        return dbPayroll.listSalaryComponents(companyId, salaryProfileId, activeOnly);
    }

    private void applyDefaults(PayrollSalaryProfile profile) {
        if (profile.getSalaryType() == null || profile.getSalaryType().isBlank()) {
            profile.setSalaryType("MONTHLY");
        }
        if (profile.getPayrollFrequency() == null || profile.getPayrollFrequency().isBlank()) {
            profile.setPayrollFrequency("MONTHLY");
        }
        if (profile.getCurrencyCode() == null || profile.getCurrencyCode().isBlank()) {
            profile.setCurrencyCode("EGP");
        } else {
            profile.setCurrencyCode(profile.getCurrencyCode().trim().toUpperCase());
        }
        if (profile.getBaseSalary() == null) {
            profile.setBaseSalary(BigDecimal.ZERO);
        }
        profile.setActive(true);
    }

    private void resolveAuthoritativeIdentity(PayrollSalaryProfile profile) {
        Employee employee;
        if (profile.getUserId() != null) {
            employee = dbHR.getEmployeeByUser(profile.getCompanyId(), profile.getUserId());
        } else if (profile.getEmployeeId() > 0 && profile.getBranchId() > 0) {
            employee = dbHR.getEmployeeById(profile.getCompanyId(), profile.getBranchId(), profile.getEmployeeId());
        } else {
            employee = null;
        }
        if (employee == null || !employee.isActive() || employee.getUserId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_COMPANY_USER_REQUIRED",
                    "Compensation can only be configured for an active user assigned to this company");
        }
        profile.setEmployeeId(employee.getId());
        profile.setUserId(employee.getUserId());
        profile.setBranchId(employee.getBranchId());
    }
}
