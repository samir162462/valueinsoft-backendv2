package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Finance.FinanceAccountBalanceRebuildResponse;
import com.example.valueinsoftbackend.Service.FinanceProjectionService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/finance/projections")
public class FinanceProjectionController {

    private final FinanceProjectionService financeProjectionService;

    public FinanceProjectionController(FinanceProjectionService financeProjectionService) {
        this.financeProjectionService = financeProjectionService;
    }

    @PostMapping("/account-balances/rebuild")
    public FinanceAccountBalanceRebuildResponse rebuildAccountBalances(Principal principal,
                                                                       @RequestParam("companyId") @Positive int companyId,
                                                                       @RequestParam("fiscalPeriodId") UUID fiscalPeriodId,
                                                                       @RequestParam(value = "currencyCode", defaultValue = "EGP")
                                                                       String currencyCode) {
        return financeProjectionService.rebuildAccountBalancesForAuthenticatedUser(
                principal.getName(),
                companyId,
                fiscalPeriodId,
                currencyCode);
    }
}
