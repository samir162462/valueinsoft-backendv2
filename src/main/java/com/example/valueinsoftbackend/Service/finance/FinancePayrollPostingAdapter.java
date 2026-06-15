package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Payroll.PayrollPayment;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRunLine;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSalaryProfile;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class FinancePayrollPostingAdapter implements FinancePostingAdapter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(8);

    private final DbPayroll dbPayroll;
    private final DbFinanceJournal dbFinanceJournal;
    private final ObjectMapper objectMapper;

    public FinancePayrollPostingAdapter(DbPayroll dbPayroll,
                                       DbFinanceJournal dbFinanceJournal,
                                       ObjectMapper objectMapper) {
        this.dbPayroll = dbPayroll;
        this.dbFinanceJournal = dbFinanceJournal;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceModule) {
        return "payroll".equals(sourceModule);
    }

    @Override
    public UUID post(FinancePostingRequestItem request) {
        if ("salary_accrual".equals(request.getSourceType())) {
            return postSalaryAccrual(request);
        }
        if ("salary_payment".equals(request.getSourceType())) {
            return postSalaryPayment(request);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYROLL_SOURCE_TYPE_UNSUPPORTED",
                "Unsupported payroll posting source type");
    }

    private java.util.UUID postSalaryAccrual(FinancePostingRequestItem request) {
        JsonNode payload = parsePayload(request);
        long runId = payload.path("payrollRunId").asLong();
        PayrollRun run = dbPayroll.getRun(request.getCompanyId(), runId);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYROLL_RUN_NOT_FOUND", "Payroll run was not found");
        }
        PayrollSettings settings = dbPayroll.getSettings(request.getCompanyId());
        if (settings == null) {
            throw new ApiException(HttpStatus.FAILED_DEPENDENCY, "PAYROLL_SETTINGS_MISSING", "Payroll settings are not configured");
        }
        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        for (PayrollRunLine line : dbPayroll.listRunLines(request.getCompanyId(), runId)) {
            PayrollSalaryProfile profile = dbPayroll.getSalaryProfile(request.getCompanyId(), line.getSalaryProfileId());
            java.util.UUID expense = (profile != null && profile.getSalaryExpenseAccountId() != null) ? profile.getSalaryExpenseAccountId() : settings.getSalaryExpenseAccountId();
            java.util.UUID payable = (profile != null && profile.getSalaryPayableAccountId() != null) ? profile.getSalaryPayableAccountId() : settings.getSalaryPayableAccountId();
            requireAccount(expense, "PAYROLL_SALARY_EXPENSE_ACCOUNT_REQUIRED");
            requireAccount(payable, "PAYROLL_SALARY_PAYABLE_ACCOUNT_REQUIRED");
            String description = "Payroll accrual " + run.getRunLabel() + " employee " + line.getEmployeeId();
            lines.add(line(expense, request.getBranchId(), line.getNetSalary(), ZERO, description));
            lines.add(line(payable, request.getBranchId(), ZERO, line.getNetSalary(), description));
        }
        java.util.UUID journalEntryId = createPostedPayrollJournal(request, "payroll.accrual", "PY-A-", "payroll_accrual",
                "Payroll accrual " + run.getRunLabel(), run.getCurrencyCode(), lines);

        run.setStatus("POSTED");
        run.setPostedJournalId(journalEntryId);
        run.setPostedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        dbPayroll.updateRun(run);

        return journalEntryId;
    }

    private java.util.UUID postSalaryPayment(FinancePostingRequestItem request) {
        JsonNode payload = parsePayload(request);
        long paymentId = payload.path("payrollPaymentId").asLong();
        PayrollPayment payment = dbPayroll.getPayment(request.getCompanyId(), paymentId);
        if (payment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYROLL_PAYMENT_NOT_FOUND", "Payroll payment was not found");
        }
        PayrollSettings settings = dbPayroll.getSettings(request.getCompanyId());
        requireAccount(settings == null ? null : settings.getSalaryPayableAccountId(), "PAYROLL_SALARY_PAYABLE_ACCOUNT_REQUIRED");
        requireAccount(settings == null ? null : settings.getCashBankAccountId(), "PAYROLL_CASH_BANK_ACCOUNT_REQUIRED");

        String description = "Payroll payment " + payment.getReferenceNumber();
        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        lines.add(line(settings.getSalaryPayableAccountId(), request.getBranchId(), payment.getTotalAmount(), ZERO, description));
        lines.add(line(settings.getCashBankAccountId(), request.getBranchId(), ZERO, payment.getTotalAmount(), description));
        java.util.UUID journalEntryId = createPostedPayrollJournal(request, "payroll.payment", "PY-P-", "payroll_payment",
                description, payment.getCurrencyCode(), lines);

        payment.setJournalId(journalEntryId);
        payment.setPostedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        dbPayroll.updatePayment(payment);

        return journalEntryId;
    }

    private UUID createPostedPayrollJournal(FinancePostingRequestItem request,
                                            String sequenceKey,
                                            String prefix,
                                            String journalType,
                                            String description,
                                            String currencyCode,
                                            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYROLL_POSTING_LINES_EMPTY", "Payroll posting has no journal lines");
        }
        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(request.getCompanyId(), sequenceKey, prefix);
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }
        BigDecimal debit = lines.stream().map(DbFinanceJournal.PostedSourceJournalLineCommand::debitAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(DbFinanceJournal.PostedSourceJournalLineCommand::creditAmount).reduce(ZERO, BigDecimal::add);
        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(new DbFinanceJournal.PostedSourceJournalCommand(
                request.getCompanyId(),
                request.getBranchId(),
                journalNumber,
                journalType,
                request.getSourceModule(),
                request.getSourceType(),
                request.getSourceId(),
                request.getPostingDate(),
                request.getFiscalPeriodId(),
                description,
                currencyCode,
                ONE,
                debit,
                credit,
                postedBy,
                lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand line(UUID accountId,
                                                                 Integer branchId,
                                                                 BigDecimal debit,
                                                                 BigDecimal credit,
                                                                 String description) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                accountId, branchId, debit, credit, description, null, null, null, null, null, null, null);
    }

    private JsonNode parsePayload(FinancePostingRequestItem request) {
        try {
            return objectMapper.readTree(request.getRequestPayloadJson());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYROLL_PAYLOAD_INVALID",
                    "Payroll posting payload is not valid JSON");
        }
    }

    private void requireAccount(UUID accountId, String code) {
        if (accountId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Payroll posting account is missing");
        }
    }
}
