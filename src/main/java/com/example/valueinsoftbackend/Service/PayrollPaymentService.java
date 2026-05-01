package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollPayment;
import com.example.valueinsoftbackend.Model.Payroll.PayrollPaymentLine;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRunLine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PayrollPaymentService {

    private final DbPayroll dbPayroll;
    private final PayrollRunService runService;
    private final PayrollValidationService validationService;
    private final PayrollPostingService postingService;
    private final PayrollAuditService auditService;

    public PayrollPaymentService(DbPayroll dbPayroll,
                                 PayrollRunService runService,
                                 PayrollValidationService validationService,
                                 PayrollPostingService postingService,
                                 PayrollAuditService auditService) {
        this.dbPayroll = dbPayroll;
        this.runService = runService;
        this.validationService = validationService;
        this.postingService = postingService;
        this.auditService = auditService;
    }

    public List<PayrollPayment> list(int companyId, Long payrollRunId) {
        return dbPayroll.listPayments(companyId, payrollRunId);
    }

    public List<PayrollPaymentLine> listLines(int companyId, long paymentId) {
        return dbPayroll.listPaymentLines(companyId, paymentId);
    }

    @Transactional
    public PayrollPayment payEmployee(String actor,
                                      int companyId,
                                      long runId,
                                      long payrollRunLineId,
                                      BigDecimal paidAmount,
                                      java.sql.Date paymentDate,
                                      String paymentMethod,
                                      String currencyCode,
                                      UUID fiscalPeriodId) {
        PayrollRun run = runService.get(companyId, runId);
        if (!"POSTED".equals(run.getStatus()) && !"PARTIALLY_PAID".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_RUN_NOT_PAYABLE",
                    "Only POSTED or PARTIALLY_PAID payroll runs can be paid");
        }
        PayrollRunLine runLine = dbPayroll.getRunLine(companyId, payrollRunLineId);
        if (runLine == null || runLine.getPayrollRunId() != runId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYROLL_RUN_LINE_NOT_FOUND",
                    "Payroll run line was not found for this run");
        }
        validationService.validatePaymentAmount(paidAmount, runLine.getRemainingAmount());

        PayrollPayment payment = new PayrollPayment();
        payment.setCompanyId(companyId);
        payment.setPayrollRunId(runId);
        payment.setPaymentDate(paymentDate);
        payment.setPaymentMethod(paymentMethod);
        payment.setTotalAmount(paidAmount);
        payment.setCurrencyCode(currencyCode == null ? run.getCurrencyCode() : currencyCode);
        payment.setStatus("COMPLETED");
        payment.setCreatedBy(actor);
        payment.setUpdatedBy(actor);
        long paymentId = dbPayroll.createPayment(payment);

        BigDecimal remaining = runLine.getRemainingAmount().subtract(paidAmount);
        PayrollPaymentLine paymentLine = new PayrollPaymentLine();
        paymentLine.setCompanyId(companyId);
        paymentLine.setPayrollPaymentId(paymentId);
        paymentLine.setPayrollRunLineId(runLine.getId());
        paymentLine.setEmployeeId(runLine.getEmployeeId());
        paymentLine.setNetSalary(runLine.getNetSalary());
        paymentLine.setPaidAmount(paidAmount);
        paymentLine.setRemainingAmount(remaining);
        paymentLine.setPaymentMethod(paymentMethod);
        paymentLine.setPaymentStatus(remaining.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "PARTIALLY_PAID");
        dbPayroll.createPaymentLine(paymentLine);

        runLine.setPaidAmount(runLine.getPaidAmount().add(paidAmount));
        runLine.setRemainingAmount(remaining);
        runLine.setPaymentStatus(paymentLine.getPaymentStatus());
        dbPayroll.updateRunLine(runLine);

        boolean allPaid = dbPayroll.listRunLines(companyId, runId).stream()
                .allMatch(line -> line.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0);
        run.setStatus(allPaid ? "PAID" : "PARTIALLY_PAID");
        run.setUpdatedBy(actor);
        dbPayroll.updateRun(run);

        PayrollPayment created = dbPayroll.getPayment(companyId, paymentId);
        postingService.enqueuePayment(actor, created, run.getBranchId(), fiscalPeriodId);
        auditService.record(companyId, run.getBranchId(), "payment", String.valueOf(paymentId),
                "EMPLOYEE_PAYMENT_CREATED", null, null, actor, "Payroll employee payment created");
        return created;
    }

    @Transactional
    public PayrollPayment payAll(String actor, int companyId, long runId, PayrollPayment request, UUID fiscalPeriodId) {
        PayrollRun run = runService.get(companyId, runId);
        if (!"POSTED".equals(run.getStatus()) && !"PARTIALLY_PAID".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_RUN_NOT_PAYABLE",
                    "Only POSTED or PARTIALLY_PAID payroll runs can be paid");
        }

        List<PayrollRunLine> payableLines = dbPayroll.listRunLines(companyId, runId).stream()
                .filter(line -> line.getRemainingAmount() != null && line.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (payableLines.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_NO_REMAINING_AMOUNT",
                    "Payroll run has no remaining salaries to pay");
        }

        BigDecimal total = payableLines.stream()
                .map(PayrollRunLine::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        request.setCompanyId(companyId);
        request.setPayrollRunId(runId);
        request.setTotalAmount(total);
        request.setCurrencyCode(request.getCurrencyCode() == null ? run.getCurrencyCode() : request.getCurrencyCode());
        request.setStatus("COMPLETED");
        request.setCreatedBy(actor);
        request.setUpdatedBy(actor);
        long paymentId = dbPayroll.createPayment(request);

        for (PayrollRunLine runLine : payableLines) {
            BigDecimal paid = runLine.getRemainingAmount();
            validationService.validatePaymentAmount(paid, runLine.getRemainingAmount());
            PayrollPaymentLine paymentLine = new PayrollPaymentLine();
            paymentLine.setCompanyId(companyId);
            paymentLine.setPayrollPaymentId(paymentId);
            paymentLine.setPayrollRunLineId(runLine.getId());
            paymentLine.setEmployeeId(runLine.getEmployeeId());
            paymentLine.setNetSalary(runLine.getNetSalary());
            paymentLine.setPaidAmount(paid);
            paymentLine.setRemainingAmount(BigDecimal.ZERO);
            paymentLine.setPaymentMethod(request.getPaymentMethod());
            paymentLine.setPaymentStatus("PAID");
            dbPayroll.createPaymentLine(paymentLine);

            runLine.setPaidAmount(runLine.getPaidAmount().add(paid));
            runLine.setRemainingAmount(BigDecimal.ZERO);
            runLine.setPaymentStatus("PAID");
            dbPayroll.updateRunLine(runLine);
        }

        run.setStatus("PAID");
        run.setUpdatedBy(actor);
        dbPayroll.updateRun(run);
        PayrollPayment payment = dbPayroll.getPayment(companyId, paymentId);
        postingService.enqueuePayment(actor, payment, run.getBranchId(), fiscalPeriodId);
        auditService.record(companyId, run.getBranchId(), "payment", String.valueOf(paymentId),
                "PAYMENT_CREATED", null, null, actor, "Payroll payment batch created");
        return dbPayroll.getPayment(companyId, paymentId);
    }
}
