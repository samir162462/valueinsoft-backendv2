package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;

@Service
public class PayrollValidationService {

    private final DbPayroll dbPayroll;
    private final DbFinanceSetup dbFinanceSetup;
    private final PayrollStateMachineService stateMachineService;

    public PayrollValidationService(DbPayroll dbPayroll,
                                    DbFinanceSetup dbFinanceSetup,
                                    PayrollStateMachineService stateMachineService) {
        this.dbPayroll = dbPayroll;
        this.dbFinanceSetup = dbFinanceSetup;
        this.stateMachineService = stateMachineService;
    }

    public void validateNoOverlap(PayrollSalaryProfile profile, Long excludeProfileId) {
        validatePeriod(profile.getEffectiveFrom(), profile.getEffectiveTo());
        if (profile.isActive() && dbPayroll.hasOverlappingActiveProfile(
                profile.getCompanyId(),
                profile.getEmployeeId(),
                profile.getEffectiveFrom(),
                profile.getEffectiveTo(),
                excludeProfileId)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_PROFILE_OVERLAP",
                    "Employee already has an active salary profile for this period");
        }
    }

    public void validatePeriod(Date start, Date end) {
        if (start == null || (end != null && start.after(end))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_PERIOD_INVALID",
                    "Period start must be present and must be before or equal to period end");
        }
    }

    public void validateAccounts(int companyId, PayrollSalaryProfile profile, PayrollSettings settings) {
        UUID expenseAccount = profile == null ? null : profile.getSalaryExpenseAccountId();
        UUID payableAccount = profile == null ? null : profile.getSalaryPayableAccountId();
        if (expenseAccount == null && settings != null) {
            expenseAccount = settings.getSalaryExpenseAccountId();
        }
        if (payableAccount == null && settings != null) {
            payableAccount = settings.getSalaryPayableAccountId();
        }
        validateAccountExists(companyId, expenseAccount, "PAYROLL_SALARY_EXPENSE_ACCOUNT_REQUIRED");
        validateAccountExists(companyId, payableAccount, "PAYROLL_SALARY_PAYABLE_ACCOUNT_REQUIRED");
    }

    public void validatePaymentAccount(int companyId, PayrollSettings settings) {
        validateAccountExists(companyId, settings == null ? null : settings.getCashBankAccountId(), "PAYROLL_CASH_BANK_ACCOUNT_REQUIRED");
    }

    public void validateRunEditable(String status) {
        stateMachineService.validateRunEditable(status);
    }

    public void validatePaymentAmount(BigDecimal amount, BigDecimal remainingAmount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_PAYMENT_AMOUNT_INVALID",
                    "Payment amount must be greater than zero");
        }
        if (remainingAmount != null && amount.compareTo(remainingAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_PAYMENT_EXCEEDS_REMAINING",
                    "Payment amount cannot exceed remaining salary");
        }
    }

    private void validateAccountExists(int companyId, UUID accountId, String code) {
        if (accountId == null || !dbFinanceSetup.accountExists(companyId, accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Required payroll finance account is missing or invalid");
        }
    }
}
