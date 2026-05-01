package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Payroll.PayrollPayment;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class PayrollPostingService {

    private final FinancePostingRequestService financePostingRequestService;

    public PayrollPostingService(FinancePostingRequestService financePostingRequestService) {
        this.financePostingRequestService = financePostingRequestService;
    }

    public FinancePostingRequestItem enqueueAccrual(String actor, PayrollRun run, UUID fiscalPeriodId) {
        if (run.getPostedJournalId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_ALREADY_POSTED", "Payroll run is already posted");
        }
        return financePostingRequestService.createPostingRequestFromSystem(actor, new FinancePostingRequestCreateRequest(
                run.getCompanyId(),
                run.getBranchId(),
                "payroll",
                "salary_accrual",
                "payroll-accrual-" + run.getCompanyId() + "-" + run.getId(),
                run.getPeriodEnd().toLocalDate(),
                fiscalPeriodId,
                Map.of("payrollRunId", run.getId(), "totalNet", run.getTotalNet(), "currencyCode", run.getCurrencyCode())
        ));
    }

    public FinancePostingRequestItem enqueuePayment(String actor, PayrollPayment payment, Integer branchId, UUID fiscalPeriodId) {
        if (payment.getJournalId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_PAYMENT_ALREADY_POSTED", "Payroll payment is already posted");
        }
        LocalDate postingDate = payment.getPaymentDate().toLocalDate();
        return financePostingRequestService.createPostingRequestFromSystem(actor, new FinancePostingRequestCreateRequest(
                payment.getCompanyId(),
                branchId,
                "payroll",
                "salary_payment",
                "payroll-payment-" + payment.getCompanyId() + "-" + payment.getId(),
                postingDate,
                fiscalPeriodId,
                Map.of("payrollPaymentId", payment.getId(), "payrollRunId", payment.getPayrollRunId(), "amount", payment.getTotalAmount(), "currencyCode", payment.getCurrencyCode())
        ));
    }
}
