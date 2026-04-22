package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceBalanceSheetResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceGeneralLedgerResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceProfitAndLossResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTrialBalanceResponse;
import com.example.valueinsoftbackend.Service.FinanceReportingService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/reports")
public class FinanceReportingController {

    private final FinanceReportingService financeReportingService;

    public FinanceReportingController(FinanceReportingService financeReportingService) {
        this.financeReportingService = financeReportingService;
    }

    @GetMapping("/trial-balance")
    public FinanceTrialBalanceResponse getTrialBalance(Principal principal,
                                                       @RequestParam("companyId") @Positive int companyId,
                                                       @RequestParam("fiscalPeriodId") UUID fiscalPeriodId,
                                                       @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                       @RequestParam(value = "currencyCode", defaultValue = "EGP") String currencyCode,
                                                       @RequestParam(value = "includeZeroBalances", defaultValue = "false")
                                                       boolean includeZeroBalances) {
        return financeReportingService.getTrialBalanceForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                includeZeroBalances);
    }

    @GetMapping("/general-ledger")
    public FinanceGeneralLedgerResponse getGeneralLedger(Principal principal,
                                                         @RequestParam("companyId") @Positive int companyId,
                                                         @RequestParam("fiscalPeriodId") UUID fiscalPeriodId,
                                                         @RequestParam("accountId") UUID accountId,
                                                         @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                         @RequestParam(value = "currencyCode", defaultValue = "EGP") String currencyCode,
                                                         @RequestParam(value = "limit", required = false) @Positive Integer limit,
                                                         @RequestParam(value = "offset", required = false) @Min(0) Integer offset) {
        return financeReportingService.getGeneralLedgerForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                accountId,
                branchId,
                currencyCode,
                limit,
                offset);
    }

    @GetMapping("/profit-and-loss")
    public FinanceProfitAndLossResponse getProfitAndLoss(Principal principal,
                                                         @RequestParam("companyId") @Positive int companyId,
                                                         @RequestParam("fiscalPeriodId") UUID fiscalPeriodId,
                                                         @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                         @RequestParam(value = "currencyCode", defaultValue = "EGP") String currencyCode,
                                                         @RequestParam(value = "includeZeroBalances", defaultValue = "false")
                                                         boolean includeZeroBalances) {
        return financeReportingService.getProfitAndLossForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                includeZeroBalances);
    }

    @GetMapping("/balance-sheet")
    public FinanceBalanceSheetResponse getBalanceSheet(Principal principal,
                                                       @RequestParam("companyId") @Positive int companyId,
                                                       @RequestParam("fiscalPeriodId") UUID fiscalPeriodId,
                                                       @RequestParam(value = "branchId", required = false) @Positive Integer branchId,
                                                       @RequestParam(value = "currencyCode", defaultValue = "EGP") String currencyCode,
                                                       @RequestParam(value = "includeZeroBalances", defaultValue = "false")
                                                       boolean includeZeroBalances) {
        return financeReportingService.getBalanceSheetForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                branchId,
                currencyCode,
                includeZeroBalances);
    }
}
