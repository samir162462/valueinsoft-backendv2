package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRun;
import com.example.valueinsoftbackend.Model.Payroll.PayrollRunLine;
import com.example.valueinsoftbackend.Model.Payroll.PayrollSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class PayrollRunService {

    private final DbPayroll dbPayroll;
    private final PayrollSettingsService settingsService;
    private final PayrollCalculationService calculationService;
    private final PayrollValidationService validationService;
    private final PayrollStateMachineService stateMachineService;
    private final PayrollAuditService auditService;

    public PayrollRunService(DbPayroll dbPayroll,
                             PayrollSettingsService settingsService,
                             PayrollCalculationService calculationService,
                             PayrollValidationService validationService,
                             PayrollStateMachineService stateMachineService,
                             PayrollAuditService auditService) {
        this.dbPayroll = dbPayroll;
        this.settingsService = settingsService;
        this.calculationService = calculationService;
        this.validationService = validationService;
        this.stateMachineService = stateMachineService;
        this.auditService = auditService;
    }

    public List<PayrollRun> list(int companyId, Integer branchId, String status) {
        return dbPayroll.listRuns(companyId, branchId, status);
    }

    public PayrollRun get(int companyId, long id) {
        PayrollRun run = dbPayroll.getRun(companyId, id);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYROLL_RUN_NOT_FOUND", "Payroll run was not found");
        }
        return run;
    }

    public List<PayrollRunLine> listLines(int companyId, long runId) {
        return dbPayroll.listRunLines(companyId, runId);
    }

    @Transactional
    public PayrollRun generate(String actor, PayrollRun request) {
        validationService.validatePeriod(request.getPeriodStart(), request.getPeriodEnd());
        request.setStatus("DRAFT");
        request.setCreatedBy(actor);
        request.setUpdatedBy(actor);
        request.setTotalGross(BigDecimal.ZERO);
        request.setTotalDeductions(BigDecimal.ZERO);
        request.setTotalNet(BigDecimal.ZERO);
        request.setEmployeeCount(0);

        long runId = dbPayroll.createRun(request);
        PayrollRun run = get(request.getCompanyId(), runId);
        run = calculateAndPersist(actor, run, "CALCULATED");
        auditService.record(run.getCompanyId(), run.getBranchId(), "payroll_run", String.valueOf(runId),
                "RUN_GENERATED", null, null, actor, "Payroll run generated");
        return run;
    }

    @Transactional
    public PayrollRun recalculate(String actor, int companyId, long runId) {
        PayrollRun run = get(companyId, runId);
        validationService.validateRunEditable(run.getStatus());
        dbPayroll.deleteRunLinesByRun(companyId, runId);
        PayrollRun updated = calculateAndPersist(actor, run, "CALCULATED");
        auditService.record(companyId, updated.getBranchId(), "payroll_run", String.valueOf(runId),
                "RUN_RECALCULATED", null, null, actor, "Payroll run recalculated");
        return updated;
    }

    @Transactional
    public PayrollRun approve(String actor, int companyId, long runId) {
        PayrollRun run = get(companyId, runId);
        stateMachineService.assertTransition(run.getStatus(), "APPROVED");
        run.setStatus("APPROVED");
        run.setApprovedBy(actor);
        run.setApprovedAt(Timestamp.from(Instant.now()));
        run.setUpdatedBy(actor);
        updateRunOrConflict(run);
        auditService.record(companyId, run.getBranchId(), "payroll_run", String.valueOf(runId),
                "RUN_APPROVED", null, null, actor, "Payroll run approved");
        return get(companyId, runId);
    }

    @Transactional
    public PayrollRun cancel(String actor, int companyId, long runId) {
        PayrollRun run = get(companyId, runId);
        stateMachineService.assertTransition(run.getStatus(), "CANCELLED");
        run.setStatus("CANCELLED");
        run.setUpdatedBy(actor);
        updateRunOrConflict(run);
        auditService.record(companyId, run.getBranchId(), "payroll_run", String.valueOf(runId),
                "RUN_CANCELLED", null, null, actor, "Payroll run cancelled");
        return get(companyId, runId);
    }

    @Transactional
    public PayrollRun markPostingInProgress(String actor, int companyId, long runId) {
        PayrollRun run = get(companyId, runId);
        stateMachineService.assertTransition(run.getStatus(), "POSTING_IN_PROGRESS");
        run.setStatus("POSTING_IN_PROGRESS");
        run.setUpdatedBy(actor);
        updateRunOrConflict(run);
        return get(companyId, runId);
    }

    @Transactional
    public PayrollRun reverse(String actor, int companyId, long runId) {
        PayrollRun run = get(companyId, runId);
        stateMachineService.assertTransition(run.getStatus(), "REVERSED");
        run.setStatus("REVERSED");
        run.setUpdatedBy(actor);
        updateRunOrConflict(run);
        auditService.record(companyId, run.getBranchId(), "payroll_run", String.valueOf(runId),
                "RUN_REVERSED", null, null, actor, "Payroll run reversed");
        return get(companyId, runId);
    }

    @Transactional
    public PayrollRun postToFinance(String actor, int companyId, long runId, java.util.UUID fiscalPeriodId, PayrollPostingService postingService) {
        PayrollRun run = get(companyId, runId);
        stateMachineService.assertTransition(run.getStatus(), "POSTING_IN_PROGRESS");
        run.setStatus("POSTING_IN_PROGRESS");
        run.setUpdatedBy(actor);
        var postingRequest = postingService.enqueueAccrual(actor, run, fiscalPeriodId);
        run.setPostingRequestId(postingRequest.getPostingRequestId());
        updateRunOrConflict(run);
        auditService.record(companyId, run.getBranchId(), "payroll_run", String.valueOf(runId),
                "RUN_POSTING_ENQUEUED", null, null, actor, "Payroll accrual posting request enqueued");
        return get(companyId, runId);
    }

    private PayrollRun calculateAndPersist(String actor, PayrollRun run, String targetStatus) {
        PayrollSettings settings = settingsService.get(run.getCompanyId());
        List<PayrollRunLine> lines = calculationService.calculateRunLines(run, settings);
        BigDecimal gross = lines.stream().map(PayrollRunLine::getGrossSalary).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductions = lines.stream().map(PayrollRunLine::getTotalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = lines.stream().map(PayrollRunLine::getNetSalary).reduce(BigDecimal.ZERO, BigDecimal::add);

        for (PayrollRunLine line : lines) {
            line.setPayrollRunId(run.getId());
            dbPayroll.createRunLine(line);
        }

        run.setStatus(targetStatus);
        run.setTotalGross(gross);
        run.setTotalDeductions(deductions);
        run.setTotalNet(net);
        run.setEmployeeCount(lines.size());
        run.setUpdatedBy(actor);
        updateRunOrConflict(run);
        return get(run.getCompanyId(), run.getId());
    }

    private void updateRunOrConflict(PayrollRun run) {
        int rows = dbPayroll.updateRun(run);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYROLL_RUN_VERSION_CONFLICT",
                    "Payroll run was modified by another request");
        }
    }
}
