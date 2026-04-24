package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationItemItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationRunItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceImportResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationProofUploadResponse;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationProofUploadRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationItemResolutionRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationRunCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportRequest;
import com.example.valueinsoftbackend.Service.FinanceReconciliationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/reconciliation-runs")
public class FinanceReconciliationController {

    private final FinanceReconciliationService financeReconciliationService;

    public FinanceReconciliationController(FinanceReconciliationService financeReconciliationService) {
        this.financeReconciliationService = financeReconciliationService;
    }

    @PostMapping
    public FinanceReconciliationRunItem createRun(Principal principal,
                                                  @Valid @RequestBody FinanceReconciliationRunCreateRequest request) {
        return financeReconciliationService.createRunForAuthenticatedUser(principal.getName(), request);
    }

    @PostMapping("/source-items/import")
    public FinanceReconciliationSourceImportResponse importSourceItems(
            Principal principal,
            @Valid @RequestBody FinanceReconciliationSourceImportRequest request) {
        return financeReconciliationService.importSourceItemsForAuthenticatedUser(principal.getName(), request);
    }

    @GetMapping("/source-items")
    public ArrayList<FinanceReconciliationSourceItem> getSourceItems(
            Principal principal,
            @RequestParam("companyId") @Positive int companyId,
            @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
            @RequestParam(value = "reconciliationType", required = false) String reconciliationType,
            @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false) @Positive Integer limit,
            @RequestParam(value = "offset", required = false) @Min(0) Integer offset) {
        return financeReconciliationService.getSourceItemsForAuthenticatedUser(
                principal.getName(),
                companyId,
                branchId,
                reconciliationType,
                sourceSystem,
                status,
                limit,
                offset);
    }

    @GetMapping
    public ArrayList<FinanceReconciliationRunItem> getRuns(Principal principal,
                                                           @RequestParam("companyId") @Positive int companyId,
                                                           @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                           @RequestParam(value = "reconciliationType", required = false)
                                                           String reconciliationType,
                                                           @RequestParam(value = "status", required = false) String status,
                                                           @RequestParam(value = "limit", required = false) @Positive Integer limit,
                                                           @RequestParam(value = "offset", required = false) @Min(0) Integer offset) {
        return financeReconciliationService.getRunsForAuthenticatedUser(
                principal.getName(),
                companyId,
                branchId,
                reconciliationType,
                status,
                limit,
                offset);
    }

    @GetMapping("/{reconciliationRunId}")
    public FinanceReconciliationRunItem getRun(Principal principal,
                                               @PathVariable("reconciliationRunId") UUID reconciliationRunId,
                                               @RequestParam("companyId") @Positive int companyId) {
        return financeReconciliationService.getRunForAuthenticatedUser(
                principal.getName(),
                companyId,
                reconciliationRunId);
    }

    @GetMapping("/{reconciliationRunId}/items")
    public ArrayList<FinanceReconciliationItemItem> getItems(Principal principal,
                                                             @PathVariable("reconciliationRunId") UUID reconciliationRunId,
                                                             @RequestParam("companyId") @Positive int companyId,
                                                             @RequestParam(value = "matchStatus", required = false)
                                                             String matchStatus,
                                                             @RequestParam(value = "resolutionStatus", required = false)
                                                             String resolutionStatus,
                                                             @RequestParam(value = "limit", required = false) @Positive Integer limit,
                                                             @RequestParam(value = "offset", required = false) @Min(0) Integer offset) {
        return financeReconciliationService.getItemsForAuthenticatedUser(
                principal.getName(),
                companyId,
                reconciliationRunId,
                matchStatus,
                resolutionStatus,
                limit,
                offset);
    }

    @PostMapping("/{reconciliationRunId}/items/{reconciliationItemId}/resolution")
    public FinanceReconciliationItemItem updateItemResolution(Principal principal,
                                                              @PathVariable("reconciliationRunId") UUID reconciliationRunId,
                                                              @PathVariable("reconciliationItemId") UUID reconciliationItemId,
                                                              @Valid @RequestBody FinanceReconciliationItemResolutionRequest request) {
        return financeReconciliationService.updateItemResolutionForAuthenticatedUser(
                principal.getName(),
                reconciliationRunId,
                reconciliationItemId,
                request);
    }

    @PostMapping("/{reconciliationRunId}/items/{reconciliationItemId}/prepare-upload")
    public FinanceReconciliationProofUploadResponse prepareProofUpload(
            Principal principal,
            @PathVariable("reconciliationRunId") UUID reconciliationRunId,
            @PathVariable("reconciliationItemId") UUID reconciliationItemId,
            @Valid @RequestBody FinanceReconciliationProofUploadRequest request) {
        return financeReconciliationService.prepareProofUploadForAuthenticatedUser(
                principal.getName(),
                reconciliationRunId,
                reconciliationItemId,
                request);
    }

    @GetMapping("/items/{reconciliationItemId}/proof-url")
    public String generateProofDownloadUrl(
            Principal principal,
            @PathVariable("reconciliationItemId") UUID reconciliationItemId,
            @RequestParam("companyId") @Positive int companyId) {
        return financeReconciliationService.generateProofDownloadUrlForAuthenticatedUser(
                principal.getName(),
                companyId,
                reconciliationItemId);
    }
}
