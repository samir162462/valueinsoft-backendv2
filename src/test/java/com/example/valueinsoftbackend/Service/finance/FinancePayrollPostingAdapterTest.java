package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRunLine;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancePayrollPostingAdapterTest {

    @Test
    void accrualPostsEarnedExpenseNetPayableAndWithholdingLiabilityBalanced() {
        DbPayroll dbPayroll = Mockito.mock(DbPayroll.class);
        DbFinanceSetup dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        DbFinanceJournal dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        FinancePayrollPostingAdapter adapter = new FinancePayrollPostingAdapter(dbPayroll, dbFinanceSetup, dbFinanceJournal, new ObjectMapper());
        UUID expense = UUID.randomUUID();
        UUID payable = UUID.randomUUID();
        UUID deductions = UUID.randomUUID();
        UUID journalId = UUID.randomUUID();

        PayrollRun run = new PayrollRun();
        run.setId(9);
        run.setCompanyId(1095);
        run.setBranchId(7);
        run.setRunLabel("July payroll");
        run.setCurrencyCode("EGP");
        PayrollRunLine line = new PayrollRunLine();
        line.setEmployeeId(11);
        line.setUserId(42);
        line.setSalaryProfileId(91);
        line.setGrossSalary(new BigDecimal("2000.0000"));
        line.setWageReductionTotal(new BigDecimal("500.0000"));
        line.setWithholdingTotal(new BigDecimal("300.0000"));
        line.setNetSalary(new BigDecimal("1200.0000"));
        PayrollSettings settings = new PayrollSettings();
        settings.setSalaryExpenseAccountId(expense);
        settings.setSalaryPayableAccountId(payable);
        settings.setDeductionPayableAccountId(deductions);
        PayrollSalaryProfile profile = new PayrollSalaryProfile();

        when(dbPayroll.getRun(1095, 9)).thenReturn(run);
        when(dbPayroll.getSettings(1095)).thenReturn(settings);
        when(dbPayroll.listRunLines(1095, 9)).thenReturn(List.of(line));
        when(dbPayroll.getSalaryProfile(1095, 91)).thenReturn(profile);
        when(dbFinanceSetup.accountExists(eq(1095), eq(expense))).thenReturn(true);
        when(dbFinanceSetup.accountExists(eq(1095), eq(payable))).thenReturn(true);
        when(dbFinanceSetup.accountExists(eq(1095), eq(deductions))).thenReturn(true);
        when(dbFinanceSetup.getAccountById(1095, expense)).thenReturn(postingAccount("expense", "debit"));
        when(dbFinanceSetup.getAccountById(1095, payable)).thenReturn(postingAccount("liability", "credit"));
        when(dbFinanceSetup.getAccountById(1095, deductions)).thenReturn(postingAccount("liability", "credit"));
        when(dbFinanceJournal.allocateSourceJournalNumber(1095, "payroll.accrual", "PY-A-")).thenReturn("PY-A-1");
        when(dbFinanceJournal.createPostedSourceJournal(any())).thenReturn(journalId);

        FinancePostingRequestItem request = new FinancePostingRequestItem();
        request.setCompanyId(1095);
        request.setBranchId(7);
        request.setSourceModule("payroll");
        request.setSourceType("salary_accrual");
        request.setSourceId("payroll-accrual-1095-9");
        request.setPostingDate(LocalDate.now());
        request.setFiscalPeriodId(UUID.randomUUID());
        request.setRequestPayloadJson("{\"payrollRunId\":9}");
        request.setCreatedBy(42);

        assertEquals(journalId, adapter.post(request));

        ArgumentCaptor<DbFinanceJournal.PostedSourceJournalCommand> command =
                ArgumentCaptor.forClass(DbFinanceJournal.PostedSourceJournalCommand.class);
        verify(dbFinanceJournal).createPostedSourceJournal(command.capture());
        assertEquals(new BigDecimal("1500.0000"), command.getValue().totalDebit());
        assertEquals(new BigDecimal("1500.0000"), command.getValue().totalCredit());
        assertEquals(3, command.getValue().lines().size());
        assertEquals(new BigDecimal("1500.0000"), command.getValue().lines().get(0).debitAmount());
        assertEquals(new BigDecimal("1200.0000"), command.getValue().lines().get(1).creditAmount());
        assertEquals(new BigDecimal("300.0000"), command.getValue().lines().get(2).creditAmount());
    }

    private FinanceAccountItem postingAccount(String type, String normalBalance) {
        FinanceAccountItem account = new FinanceAccountItem();
        account.setAccountType(type);
        account.setNormalBalance(normalBalance);
        account.setPostable(true);
        account.setStatus("active");
        return account;
    }
}
