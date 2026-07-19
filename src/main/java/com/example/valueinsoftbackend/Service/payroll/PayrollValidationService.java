package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
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
        boolean overlap = profile.getUserId() != null
                ? dbPayroll.hasOverlappingActiveProfileByUser(profile.getCompanyId(), profile.getUserId(), profile.getEffectiveFrom(), profile.getEffectiveTo(), excludeProfileId)
                : dbPayroll.hasOverlappingActiveProfile(profile.getCompanyId(), profile.getEmployeeId(), profile.getEffectiveFrom(), profile.getEffectiveTo(), excludeProfileId);
        if (profile.isActive() && overlap) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_PROFILE_OVERLAP",
                    "Employee already has an active salary profile for this period");
        }
        if (profile.getBaseSalary() != null && profile.getBaseSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_BASE_SALARY_INVALID",
                    "Base salary cannot be negative");
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
        validatePostingAccount(companyId, expenseAccount, "expense", "debit",
                "PAYROLL_SALARY_EXPENSE_ACCOUNT_REQUIRED", "PAYROLL_SALARY_EXPENSE_ACCOUNT_TYPE_INVALID");
        validatePostingAccount(companyId, payableAccount, "liability", "credit",
                "PAYROLL_SALARY_PAYABLE_ACCOUNT_REQUIRED", "PAYROLL_SALARY_PAYABLE_ACCOUNT_TYPE_INVALID");
    }

    public void validatePaymentAccount(int companyId, PayrollSettings settings) {
        validatePostingAccount(companyId, settings == null ? null : settings.getCashBankAccountId(), "asset", "debit",
                "PAYROLL_CASH_BANK_ACCOUNT_REQUIRED", "PAYROLL_CASH_BANK_ACCOUNT_TYPE_INVALID");
    }

    public void validateSettingsAccounts(PayrollSettings settings) {
        if (settings == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_SETTINGS_REQUIRED", "Payroll settings are required");
        }
        validateAccounts(settings.getCompanyId(), null, settings);
        validateOptionalPostingAccount(settings.getCompanyId(), settings.getDeductionPayableAccountId(), "liability", "credit",
                "PAYROLL_DEDUCTION_PAYABLE_ACCOUNT_TYPE_INVALID");
        validateOptionalPostingAccount(settings.getCompanyId(), settings.getCashBankAccountId(), "asset", "debit",
                "PAYROLL_CASH_BANK_ACCOUNT_TYPE_INVALID");
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

    private void validateOptionalPostingAccount(int companyId,
                                                UUID accountId,
                                                String expectedType,
                                                String expectedNormalBalance,
                                                String typeCode) {
        if (accountId != null) {
            validatePostingAccount(companyId, accountId, expectedType, expectedNormalBalance, typeCode, typeCode);
        }
    }

    private void validatePostingAccount(int companyId,
                                        UUID accountId,
                                        String expectedType,
                                        String expectedNormalBalance,
                                        String missingCode,
                                        String typeCode) {
        if (accountId == null || !dbFinanceSetup.accountExists(companyId, accountId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, missingCode, "Required payroll finance account is missing or invalid");
        }
        FinanceAccountItem account = dbFinanceSetup.getAccountById(companyId, accountId);
        if (!expectedType.equalsIgnoreCase(account.getAccountType())
                || !expectedNormalBalance.equalsIgnoreCase(account.getNormalBalance())
                || !account.isPostable()
                || !"active".equalsIgnoreCase(account.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, typeCode,
                    "Payroll account must be an active, postable " + expectedType + " account with a " + expectedNormalBalance + " balance");
        }
    }
}
