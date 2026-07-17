package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReconModels;
import com.example.valueinsoftbackend.Service.openitems.OpenItemsReconciliationService;
import com.example.valueinsoftbackend.Service.openitems.OpeningBalanceImportService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Stage 6 endpoints: reconciliation snapshot (6.1), staging reports (6.2), and the gated
 * opening-balance import (6.3). Reads use finance.entry.read, the import uses
 * finance.entry.edit — the same capabilities the finance reconciliation module already
 * uses, so existing Owner/Accountant grants apply without new seeds.
 */
@RestController
@RequestMapping("/finance/openitems")
public class OpenItemsReconciliationController {

    private final OpenItemsReconciliationService reconciliation;
    private final OpeningBalanceImportService importService;
    private final AuthorizationService authorization;

    public OpenItemsReconciliationController(OpenItemsReconciliationService reconciliation,
                                             OpeningBalanceImportService importService,
                                             AuthorizationService authorization) {
        this.reconciliation = reconciliation;
        this.importService = importService;
        this.authorization = authorization;
    }

    @GetMapping("/{companyId}/reconciliation")
    public OpenItemsReconModels.ReconciliationSnapshot snapshot(@PathVariable @Positive int companyId,
                                                                Principal principal) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, null, "finance.entry.read");
        return reconciliation.snapshot(companyId);
    }

    @GetMapping("/{companyId}/staging/ar")
    public OpenItemsReconModels.ArStagingReport arStaging(@PathVariable @Positive int companyId,
                                                          Principal principal) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, null, "finance.entry.read");
        return reconciliation.arStagingReport(companyId);
    }

    @GetMapping("/{companyId}/staging/ap")
    public OpenItemsReconModels.ApStagingReport apStaging(@PathVariable @Positive int companyId,
                                                          Principal principal) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, null, "finance.entry.read");
        return reconciliation.apStagingReport(companyId);
    }

    @GetMapping("/{companyId}/opening-imports")
    public List<OpenItemsReconModels.ImportRunAuditRow> importRuns(@PathVariable @Positive int companyId,
                                                                   @RequestParam(required = false) String side,
                                                                   @RequestParam(defaultValue = "50") int limit,
                                                                   Principal principal) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, null, "finance.entry.read");
        return reconciliation.importRuns(companyId, side, limit);
    }

    @PostMapping("/{companyId}/opening-imports")
    public OpenItemsReconModels.OpeningImportResult importOpeningBalances(
            @PathVariable @Positive int companyId,
            @Valid @RequestBody OpenItemsReconModels.OpeningImportCommand command,
            Principal principal) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, null, "finance.entry.edit");
        return importService.importOpeningBalances(companyId, command, principal.getName());
    }
}
