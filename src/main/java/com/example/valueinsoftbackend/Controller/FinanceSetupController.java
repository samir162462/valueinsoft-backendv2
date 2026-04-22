package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalPeriodItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceFiscalYearItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceSetupBundleResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceSetupOverviewResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceTaxCodeItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountMappingCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountMappingUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceAccountUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalPeriodCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalPeriodUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalYearCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceFiscalYearUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceTaxCodeCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceTaxCodeUpdateRequest;
import com.example.valueinsoftbackend.Service.FinanceSetupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/setup")
public class FinanceSetupController {

    private final FinanceSetupService financeSetupService;

    public FinanceSetupController(FinanceSetupService financeSetupService) {
        this.financeSetupService = financeSetupService;
    }

    @GetMapping("/overview")
    public FinanceSetupOverviewResponse getOverview(Principal principal,
                                                    @RequestParam("companyId") @Positive int companyId) {
        return financeSetupService.getOverviewForAuthenticatedUser(principal.getName(), companyId);
    }

    @GetMapping("/bundle")
    public FinanceSetupBundleResponse getSetupBundle(Principal principal,
                                                     @RequestParam("companyId") @Positive int companyId,
                                                     @RequestParam(value = "branchId", required = false) @Positive Integer branchId) {
        return financeSetupService.getSetupBundleForAuthenticatedUser(principal.getName(), companyId, branchId);
    }

    @GetMapping("/fiscal-years")
    public ArrayList<FinanceFiscalYearItem> getFiscalYears(Principal principal,
                                                           @RequestParam("companyId") @Positive int companyId) {
        return financeSetupService.getFiscalYearsForAuthenticatedUser(principal.getName(), companyId);
    }

    @PostMapping("/fiscal-years")
    public FinanceFiscalYearItem createFiscalYear(Principal principal,
                                                  @Valid @RequestBody FinanceFiscalYearCreateRequest request) {
        return financeSetupService.createFiscalYearForAuthenticatedUser(principal.getName(), request);
    }

    @PutMapping("/fiscal-years/{fiscalYearId}")
    public FinanceFiscalYearItem updateFiscalYear(Principal principal,
                                                  @PathVariable("fiscalYearId") UUID fiscalYearId,
                                                  @Valid @RequestBody FinanceFiscalYearUpdateRequest request) {
        return financeSetupService.updateFiscalYearForAuthenticatedUser(principal.getName(), fiscalYearId, request);
    }

    @GetMapping("/fiscal-periods")
    public ArrayList<FinanceFiscalPeriodItem> getFiscalPeriods(Principal principal,
                                                               @RequestParam("companyId") @Positive int companyId) {
        return financeSetupService.getFiscalPeriodsForAuthenticatedUser(principal.getName(), companyId);
    }

    @PostMapping("/fiscal-periods")
    public FinanceFiscalPeriodItem createFiscalPeriod(Principal principal,
                                                      @Valid @RequestBody FinanceFiscalPeriodCreateRequest request) {
        return financeSetupService.createFiscalPeriodForAuthenticatedUser(principal.getName(), request);
    }

    @PutMapping("/fiscal-periods/{fiscalPeriodId}")
    public FinanceFiscalPeriodItem updateFiscalPeriod(Principal principal,
                                                      @PathVariable("fiscalPeriodId") UUID fiscalPeriodId,
                                                      @Valid @RequestBody FinanceFiscalPeriodUpdateRequest request) {
        return financeSetupService.updateFiscalPeriodForAuthenticatedUser(principal.getName(), fiscalPeriodId, request);
    }

    @GetMapping("/accounts")
    public ArrayList<FinanceAccountItem> getAccounts(Principal principal,
                                                     @RequestParam("companyId") @Positive int companyId) {
        return financeSetupService.getAccountsForAuthenticatedUser(principal.getName(), companyId);
    }

    @PostMapping("/accounts")
    public FinanceAccountItem createAccount(Principal principal,
                                            @Valid @RequestBody FinanceAccountCreateRequest request) {
        return financeSetupService.createAccountForAuthenticatedUser(principal.getName(), request);
    }

    @PutMapping("/accounts/{accountId}")
    public FinanceAccountItem updateAccount(Principal principal,
                                            @PathVariable("accountId") UUID accountId,
                                            @Valid @RequestBody FinanceAccountUpdateRequest request) {
        return financeSetupService.updateAccountForAuthenticatedUser(principal.getName(), accountId, request);
    }

    @GetMapping("/account-mappings")
    public ArrayList<FinanceAccountMappingItem> getAccountMappings(Principal principal,
                                                                   @RequestParam("companyId") @Positive int companyId,
                                                                   @RequestParam(value = "branchId", required = false) @Positive Integer branchId) {
        return financeSetupService.getAccountMappingsForAuthenticatedUser(principal.getName(), companyId, branchId);
    }

    @PostMapping("/account-mappings")
    public FinanceAccountMappingItem createAccountMapping(Principal principal,
                                                          @Valid @RequestBody FinanceAccountMappingCreateRequest request) {
        return financeSetupService.createAccountMappingForAuthenticatedUser(principal.getName(), request);
    }

    @PutMapping("/account-mappings/{accountMappingId}")
    public FinanceAccountMappingItem updateAccountMapping(Principal principal,
                                                          @PathVariable("accountMappingId") UUID accountMappingId,
                                                          @Valid @RequestBody FinanceAccountMappingUpdateRequest request) {
        return financeSetupService.updateAccountMappingForAuthenticatedUser(principal.getName(), accountMappingId, request);
    }

    @GetMapping("/tax-codes")
    public ArrayList<FinanceTaxCodeItem> getTaxCodes(Principal principal,
                                                     @RequestParam("companyId") @Positive int companyId) {
        return financeSetupService.getTaxCodesForAuthenticatedUser(principal.getName(), companyId);
    }

    @PostMapping("/tax-codes")
    public FinanceTaxCodeItem createTaxCode(Principal principal,
                                            @Valid @RequestBody FinanceTaxCodeCreateRequest request) {
        return financeSetupService.createTaxCodeForAuthenticatedUser(principal.getName(), request);
    }

    @PutMapping("/tax-codes/{taxCodeId}")
    public FinanceTaxCodeItem updateTaxCode(Principal principal,
                                            @PathVariable("taxCodeId") UUID taxCodeId,
                                            @Valid @RequestBody FinanceTaxCodeUpdateRequest request) {
        return financeSetupService.updateTaxCodeForAuthenticatedUser(principal.getName(), taxCodeId, request);
    }
}
