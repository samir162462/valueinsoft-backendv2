package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceClosingEntryResponse;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseValidationResponse;
import com.example.valueinsoftbackend.Model.Finance.FinancePeriodCloseRunResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceSnapshotResponse;
import com.example.valueinsoftbackend.Service.FinancePeriodCloseService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/period-close")
public class FinancePeriodCloseController {

    private final FinancePeriodCloseService financePeriodCloseService;

    public FinancePeriodCloseController(FinancePeriodCloseService financePeriodCloseService) {
        this.financePeriodCloseService = financePeriodCloseService;
    }

    @GetMapping("/{fiscalPeriodId}/validation")
    public FinancePeriodCloseValidationResponse validatePeriodClose(Principal principal,
                                                                    @PathVariable("fiscalPeriodId") UUID fiscalPeriodId,
                                                                    @RequestParam("companyId") @Positive int companyId,
                                                                    @RequestParam(value = "currencyCode", defaultValue = "EGP")
                                                                    String currencyCode) {
        return financePeriodCloseService.validatePeriodCloseForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                currencyCode);
    }

    @PostMapping("/{fiscalPeriodId}/trial-balance-snapshots")
    public FinanceTrialBalanceSnapshotResponse generateTrialBalanceSnapshot(Principal principal,
                                                                           @PathVariable("fiscalPeriodId") UUID fiscalPeriodId,
                                                                           @RequestParam("companyId") @Positive int companyId,
                                                                           @RequestParam(value = "snapshotType", defaultValue = "pre_close")
                                                                           String snapshotType,
                                                                           @RequestParam(value = "includesClosingEntries", defaultValue = "true")
                                                                           boolean includesClosingEntries,
                                                                           @RequestParam(value = "currencyCode", defaultValue = "EGP")
                                                                           String currencyCode) {
        return financePeriodCloseService.generateTrialBalanceSnapshotForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                snapshotType,
                includesClosingEntries,
                currencyCode);
    }

    @PostMapping("/{fiscalPeriodId}/closing-entries")
    public FinanceClosingEntryResponse generateClosingEntries(Principal principal,
                                                              @PathVariable("fiscalPeriodId") UUID fiscalPeriodId,
                                                              @RequestParam("companyId") @Positive int companyId,
                                                              @RequestParam(value = "currencyCode", defaultValue = "EGP")
                                                              String currencyCode) {
        return financePeriodCloseService.generateClosingEntriesForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                currencyCode);
    }

    @PostMapping("/{fiscalPeriodId}/close")
    public FinancePeriodCloseRunResponse closePeriod(Principal principal,
                                                     @PathVariable("fiscalPeriodId") UUID fiscalPeriodId,
                                                     @RequestParam("companyId") @Positive int companyId,
                                                     @RequestParam("trialBalanceSnapshotId") UUID trialBalanceSnapshotId,
                                                     @RequestParam("expectedVersion") @Min(1) int expectedVersion) {
        return financePeriodCloseService.closePeriodForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                trialBalanceSnapshotId,
                expectedVersion);
    }

    @PostMapping("/{fiscalPeriodId}/reopen")
    public FinancePeriodCloseRunResponse reopenPeriod(Principal principal,
                                                      @PathVariable("fiscalPeriodId") UUID fiscalPeriodId,
                                                      @RequestParam("companyId") @Positive int companyId,
                                                      @RequestParam("expectedVersion") @Min(1) int expectedVersion,
                                                      @RequestParam("reason") String reason) {
        return financePeriodCloseService.reopenPeriodForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                expectedVersion,
                reason);
    }
}
