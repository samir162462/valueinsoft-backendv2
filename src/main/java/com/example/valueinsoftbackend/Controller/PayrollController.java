package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.DatabaseRequests.DbPayroll;
import com.example.valueinsoftbackend.Model.Payroll.*;
import com.example.valueinsoftbackend.Service.*;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.security.Principal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final AuthorizationService authorizationService;
    private final DbPayroll dbPayroll;
    private final PayrollSettingsService settingsService;
    private final PayrollSalaryProfileService salaryProfileService;
    private final CurrentSalaryService currentSalaryService;
    private final PayrollAdjustmentService adjustmentService;
    private final PayrollRunService runService;
    private final PayrollPostingService postingService;
    private final PayrollPaymentService paymentService;

    public PayrollController(AuthorizationService authorizationService,
                             DbPayroll dbPayroll,
                             PayrollSettingsService settingsService,
                             PayrollSalaryProfileService salaryProfileService,
                             CurrentSalaryService currentSalaryService,
                             PayrollAdjustmentService adjustmentService,
                             PayrollRunService runService,
                             PayrollPostingService postingService,
                             PayrollPaymentService paymentService) {
        this.authorizationService = authorizationService;
        this.dbPayroll = dbPayroll;
        this.settingsService = settingsService;
        this.salaryProfileService = salaryProfileService;
        this.currentSalaryService = currentSalaryService;
        this.adjustmentService = adjustmentService;
        this.runService = runService;
        this.postingService = postingService;
        this.paymentService = paymentService;
    }

    @GetMapping("/{companyId}/settings")
    public PayrollSettings getSettings(Principal principal, @PathVariable int companyId) {
        authorize(principal, companyId, null, "payroll.settings.read");
        return settingsService.get(companyId);
    }

    @PutMapping("/{companyId}/settings")
    public PayrollSettings updateSettings(Principal principal, @PathVariable int companyId, @RequestBody PayrollSettings settings) {
        authorize(principal, companyId, null, "payroll.settings.edit");
        settings.setCompanyId(companyId);
        return settingsService.update(principal.getName(), settings);
    }

    @GetMapping("/{companyId}/current-salaries")
    public List<CurrentSalaryView> currentSalaries(Principal principal,
                                                   @PathVariable int companyId,
                                                   @RequestParam(required = false) Integer branchId,
                                                   @RequestParam(required = false, defaultValue = "ALL") String filter) {
        authorize(principal, companyId, branchId, "payroll.profile.read");
        return currentSalaryService.listAll(companyId, branchId, filter);
    }

    @GetMapping("/{companyId}/salary-profiles")
    public List<PayrollSalaryProfile> salaryProfiles(Principal principal,
                                                     @PathVariable int companyId,
                                                     @RequestParam(required = false) Integer branchId,
                                                     @RequestParam(required = false) Integer employeeId,
                                                     @RequestParam(required = false) Boolean activeOnly) {
        authorize(principal, companyId, branchId, "payroll.profile.read");
        return salaryProfileService.listByCompany(companyId, branchId, employeeId, activeOnly);
    }

    @PostMapping("/{companyId}/salary-profiles")
    public PayrollSalaryProfile createSalaryProfile(Principal principal, @PathVariable int companyId, @RequestBody PayrollSalaryProfile profile) {
        authorize(principal, companyId, profile.getBranchId(), "payroll.profile.create");
        profile.setCompanyId(companyId);
        return salaryProfileService.create(principal.getName(), profile);
    }

    @PutMapping("/{companyId}/salary-profiles/{id}")
    public PayrollSalaryProfile updateSalaryProfile(Principal principal,
                                                    @PathVariable int companyId,
                                                    @PathVariable long id,
                                                    @RequestBody PayrollSalaryProfile profile) {
        authorize(principal, companyId, profile.getBranchId(), "payroll.profile.edit");
        profile.setCompanyId(companyId);
        return salaryProfileService.update(principal.getName(), id, profile);
    }

    @PostMapping("/{companyId}/salary-profiles/{id}/deactivate")
    public PayrollSalaryProfile deactivateSalaryProfile(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.profile.deactivate");
        return salaryProfileService.deactivate(principal.getName(), companyId, id);
    }

    @GetMapping("/{companyId}/allowance-types")
    public List<PayrollAllowanceType> allowanceTypes(Principal principal,
                                                     @PathVariable int companyId,
                                                     @RequestParam(required = false) Boolean activeOnly) {
        authorize(principal, companyId, null, "payroll.profile.read");
        return dbPayroll.listAllowanceTypes(companyId, activeOnly);
    }

    @PostMapping("/{companyId}/allowance-types")
    public PayrollAllowanceType createAllowanceType(Principal principal, @PathVariable int companyId, @RequestBody PayrollAllowanceType type) {
        authorize(principal, companyId, null, "payroll.profile.create");
        type.setCompanyId(companyId);
        type.setCreatedBy(principal.getName());
        type.setUpdatedBy(principal.getName());
        int id = dbPayroll.createAllowanceType(type);
        return dbPayroll.getAllowanceType(companyId, id);
    }

    @GetMapping("/{companyId}/deduction-types")
    public List<PayrollDeductionType> deductionTypes(Principal principal,
                                                     @PathVariable int companyId,
                                                     @RequestParam(required = false) Boolean activeOnly) {
        authorize(principal, companyId, null, "payroll.profile.read");
        return dbPayroll.listDeductionTypes(companyId, activeOnly);
    }

    @PostMapping("/{companyId}/deduction-types")
    public PayrollDeductionType createDeductionType(Principal principal, @PathVariable int companyId, @RequestBody PayrollDeductionType type) {
        authorize(principal, companyId, null, "payroll.profile.create");
        type.setCompanyId(companyId);
        type.setCreatedBy(principal.getName());
        type.setUpdatedBy(principal.getName());
        int id = dbPayroll.createDeductionType(type);
        return dbPayroll.getDeductionType(companyId, id);
    }

    @GetMapping("/{companyId}/adjustments")
    public List<PayrollAdjustment> adjustments(Principal principal,
                                               @PathVariable int companyId,
                                               @RequestParam(required = false) Integer branchId,
                                               @RequestParam(required = false) Integer employeeId,
                                               @RequestParam(required = false) String status) {
        authorize(principal, companyId, branchId, "payroll.adjustment.read");
        return adjustmentService.list(companyId, branchId, employeeId, status);
    }

    @PostMapping("/{companyId}/adjustments")
    public PayrollAdjustment createAdjustment(Principal principal, @PathVariable int companyId, @RequestBody PayrollAdjustment adjustment) {
        authorize(principal, companyId, adjustment.getBranchId(), "payroll.adjustment.create");
        adjustment.setCompanyId(companyId);
        return adjustmentService.create(principal.getName(), adjustment);
    }

    @PostMapping("/{companyId}/adjustments/{id}/approve")
    public PayrollAdjustment approveAdjustment(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.adjustment.approve");
        return adjustmentService.approve(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/adjustments/{id}/reject")
    public PayrollAdjustment rejectAdjustment(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.adjustment.approve");
        return adjustmentService.reject(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/adjustments/{id}/cancel")
    public PayrollAdjustment cancelAdjustment(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.adjustment.approve");
        return adjustmentService.cancel(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/runs/generate")
    public PayrollRun generateRun(Principal principal, @PathVariable int companyId, @RequestBody PayrollRun run) {
        authorize(principal, companyId, run.getBranchId(), "payroll.run.create");
        run.setCompanyId(companyId);
        return runService.generate(principal.getName(), run);
    }

    @GetMapping("/{companyId}/runs")
    public List<PayrollRun> runs(Principal principal,
                                 @PathVariable int companyId,
                                 @RequestParam(required = false) Integer branchId,
                                 @RequestParam(required = false) String status) {
        authorize(principal, companyId, branchId, "payroll.run.read");
        return runService.list(companyId, branchId, status);
    }

    @GetMapping("/{companyId}/runs/{id}")
    public PayrollRun run(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.run.read");
        return runService.get(companyId, id);
    }

    @GetMapping("/{companyId}/runs/{id}/lines")
    public List<PayrollRunLine> runLines(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.run.read");
        return runService.listLines(companyId, id);
    }

    @PostMapping("/{companyId}/runs/{id}/recalculate")
    public PayrollRun recalculateRun(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.run.recalculate");
        return runService.recalculate(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/runs/{id}/approve")
    public PayrollRun approveRun(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.run.approve");
        return runService.approve(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/runs/{id}/post-to-finance")
    public PayrollRun postRunToFinance(Principal principal,
                                       @PathVariable int companyId,
                                       @PathVariable long id,
                                       @RequestParam UUID fiscalPeriodId) {
        authorize(principal, companyId, null, "payroll.run.post");
        return runService.postToFinance(principal.getName(), companyId, id, fiscalPeriodId, postingService);
    }

    @PostMapping("/{companyId}/runs/{id}/cancel")
    public PayrollRun cancelRun(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.run.recalculate");
        return runService.cancel(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/runs/{id}/reverse")
    public PayrollRun reverseRun(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.run.reverse");
        return runService.reverse(principal.getName(), companyId, id);
    }

    @PostMapping("/{companyId}/runs/{id}/payments/pay-all")
    public PayrollPayment payAll(Principal principal,
                                 @PathVariable int companyId,
                                 @PathVariable long id,
                                 @RequestParam UUID fiscalPeriodId,
                                 @RequestBody PayrollPayment payment) {
        authorize(principal, companyId, null, "payroll.payment.create");
        return paymentService.payAll(principal.getName(), companyId, id, payment, fiscalPeriodId);
    }

    @PostMapping("/{companyId}/runs/{id}/payments/pay-employee")
    public PayrollPayment payEmployee(Principal principal,
                                      @PathVariable int companyId,
                                      @PathVariable long id,
                                      @RequestParam UUID fiscalPeriodId,
                                      @RequestBody Map<String, Object> payload) {
        authorize(principal, companyId, null, "payroll.payment.create");
        return paymentService.payEmployee(
                principal.getName(),
                companyId,
                id,
                longValue(payload.get("payrollRunLineId")),
                decimalValue(payload.get("paidAmount")),
                Date.valueOf(String.valueOf(payload.get("paymentDate"))),
                stringValue(payload.get("paymentMethod")),
                stringValue(payload.get("currencyCode")),
                fiscalPeriodId);
    }

    @GetMapping("/{companyId}/runs/{id}/payments")
    public List<PayrollPayment> payments(Principal principal, @PathVariable int companyId, @PathVariable long id) {
        authorize(principal, companyId, null, "payroll.payment.read");
        return paymentService.list(companyId, id);
    }

    @GetMapping("/{companyId}/payments/{paymentId}/lines")
    public List<PayrollPaymentLine> paymentLines(Principal principal, @PathVariable int companyId, @PathVariable long paymentId) {
        authorize(principal, companyId, null, "payroll.payment.read");
        return paymentService.listLines(companyId, paymentId);
    }

    @GetMapping("/{companyId}/audit-log")
    public List<PayrollAuditLog> auditLog(Principal principal,
                                          @PathVariable int companyId,
                                          @RequestParam(required = false) String entityType,
                                          @RequestParam(required = false) String entityId) {
        authorize(principal, companyId, null, "payroll.audit.read");
        return dbPayroll.listAuditLogs(companyId, entityType, entityId);
    }

    @GetMapping(value = "/{companyId}/runs/{id}/payslips/{employeeId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> payslip(Principal principal,
                                          @PathVariable int companyId,
                                          @PathVariable long id,
                                          @PathVariable int employeeId) {
        authorize(principal, companyId, null, "payroll.run.read");
        PayrollRun run = runService.get(companyId, id);
        PayrollRunLine line = runService.listLines(companyId, id).stream()
                .filter(item -> item.getEmployeeId() == employeeId)
                .findFirst()
                .orElseThrow(() -> new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "PAYROLL_PAYSLIP_NOT_FOUND",
                        "Payslip line was not found"));
        String html = "<!doctype html><html><head><title>Payslip</title><style>" +
                "body{font-family:Arial,sans-serif;margin:32px;color:#17202a}table{border-collapse:collapse;width:100%;margin-top:18px}td,th{border:1px solid #d9e2ec;padding:10px;text-align:left}h1{margin-bottom:4px}.muted{color:#64748b}" +
                "</style></head><body><h1>Payslip</h1><div class='muted'>" + escape(run.getRunLabel()) + " · " + run.getPeriodStart() + " to " + run.getPeriodEnd() + "</div>" +
                "<table><tbody>" +
                row("Employee ID", String.valueOf(line.getEmployeeId())) +
                row("Base salary", String.valueOf(line.getBaseSalary())) +
                row("Allowances", String.valueOf(line.getTotalAllowances())) +
                row("Deductions", String.valueOf(line.getTotalDeductions())) +
                row("Gross salary", String.valueOf(line.getGrossSalary())) +
                row("Net salary", String.valueOf(line.getNetSalary())) +
                row("Paid amount", String.valueOf(line.getPaidAmount())) +
                row("Remaining amount", String.valueOf(line.getRemainingAmount())) +
                "</tbody></table></body></html>";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=payslip-" + id + "-" + employeeId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(renderPdf(html));
    }

    private void authorize(Principal principal, int companyId, Integer branchId, String capability) {
        authorizationService.assertAuthenticatedCapability(principal.getName(), companyId, branchId, capability);
    }

    private long longValue(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal decimalValue(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String row(String label, String value) {
        return "<tr><th>" + escape(label) + "</th><td>" + escape(value) + "</td></tr>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private byte[] renderPdf(String html) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYROLL_PAYSLIP_RENDER_FAILED",
                    "Unable to render payslip PDF");
        }
    }
}
