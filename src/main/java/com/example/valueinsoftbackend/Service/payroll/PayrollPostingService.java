package com.example.valueinsoftbackend.Service.payroll;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Payroll.PayrollPayment;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import com.example.valueinsoftbackend.Service.finance.FinancePostingRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class PayrollPostingService {

    private final FinancePostingRequestService financePostingRequestService;
    private final DbFinanceSetup dbFinanceSetup;

    public PayrollPostingService(FinancePostingRequestService financePostingRequestService,
                                 DbFinanceSetup dbFinanceSetup) {
        this.financePostingRequestService = financePostingRequestService;
        this.dbFinanceSetup = dbFinanceSetup;
    }

    public FinancePostingRequestItem enqueueAccrual(String actor, PayrollRun run, UUID fiscalPeriodId) {
        if (run.getPostedJournalId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_ALREADY_POSTED", "Payroll run is already posted");
        }
        UUID resolvedPeriodId = resolveFiscalPeriod(run.getCompanyId(), run.getPeriodEnd().toLocalDate(), fiscalPeriodId);
        return financePostingRequestService.createPostingRequestFromSystem(actor, new FinancePostingRequestCreateRequest(
                run.getCompanyId(),
                run.getBranchId(),
                "payroll",
                "salary_accrual",
                "payroll-accrual-" + run.getCompanyId() + "-" + run.getId(),
                run.getPeriodEnd().toLocalDate(),
                resolvedPeriodId,
                Map.of("payrollRunId", run.getId(), "totalNet", run.getTotalNet(), "currencyCode", run.getCurrencyCode())
        ));
    }

    public FinancePostingRequestItem enqueuePayment(String actor, PayrollPayment payment, Integer branchId, UUID fiscalPeriodId) {
        if (payment.getJournalId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_PAYMENT_ALREADY_POSTED", "Payroll payment is already posted");
        }
        LocalDate postingDate = payment.getPaymentDate().toLocalDate();
        UUID resolvedPeriodId = resolveFiscalPeriod(payment.getCompanyId(), postingDate, fiscalPeriodId);
        return financePostingRequestService.createPostingRequestFromSystem(actor, new FinancePostingRequestCreateRequest(
                payment.getCompanyId(),
                branchId,
                "payroll",
                "salary_payment",
                "payroll-payment-" + payment.getCompanyId() + "-" + payment.getId(),
                postingDate,
                resolvedPeriodId,
                Map.of("payrollPaymentId", payment.getId(), "payrollRunId", payment.getPayrollRunId(), "amount", payment.getTotalAmount(), "currencyCode", payment.getCurrencyCode())
        ));
    }

    private UUID resolveFiscalPeriod(int companyId, LocalDate postingDate, UUID requestedPeriodId) {
        if (requestedPeriodId != null) {
            return requestedPeriodId;
        }
        UUID resolved = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (resolved == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_OPEN_FISCAL_PERIOD_REQUIRED",
                    "No open or soft-locked finance period contains the payroll posting date");
        }
        return resolved;
    }
}
