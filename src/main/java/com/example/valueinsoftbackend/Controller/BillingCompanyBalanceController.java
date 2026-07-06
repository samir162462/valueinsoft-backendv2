package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Billing.BillingAccountBalanceResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountLedgerPageResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceCreditRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingBalanceCreditResponse;
import com.example.valueinsoftbackend.Service.billing.BillingBalanceService;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/billing/companies")
public class BillingCompanyBalanceController {

    private final BillingBalanceService billingBalanceService;
    private final PlatformAuthorizationService platformAuthorizationService;

    public BillingCompanyBalanceController(BillingBalanceService billingBalanceService,
                                           PlatformAuthorizationService platformAuthorizationService) {
        this.billingBalanceService = billingBalanceService;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    @GetMapping("/{companyId}/balance")
    public BillingAccountBalanceResponse getBalance(Principal principal,
                                                    @PathVariable @Positive int companyId,
                                                    @RequestParam(value = "currencyCode", required = false) String currencyCode) {
        platformAuthorizationService.requirePlatformCapability(principal.getName(), "platform.billing.read");
        return billingBalanceService.getBalance(companyId, currencyCode);
    }

    @GetMapping("/{companyId}/balance-ledger")
    public BillingAccountLedgerPageResponse getLedger(Principal principal,
                                                      @PathVariable @Positive int companyId,
                                                      @RequestParam(value = "currencyCode", required = false) String currencyCode,
                                                      @RequestParam(value = "transactionType", required = false) String transactionType,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        platformAuthorizationService.requirePlatformCapability(principal.getName(), "platform.billing.read");
        return billingBalanceService.getLedger(companyId, currencyCode, transactionType, page, size);
    }

    @PostMapping("/{companyId}/balance/credits")
    public ResponseEntity<BillingBalanceCreditResponse> creditBalance(Principal principal,
                                                                      @PathVariable @Positive int companyId,
                                                                      @Valid @RequestBody BillingBalanceCreditRequest request) {
        platformAuthorizationService.requirePlatformCapability(principal.getName(), "platform.billing.balance.write");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(billingBalanceService.creditBalance(companyId, request, principal.getName()));
    }

    @PostMapping("/{companyId}/balance/reversals")
    public ResponseEntity<BillingBalanceCreditResponse> reverseBalance(Principal principal,
                                                                       @PathVariable @Positive int companyId,
                                                                       @Valid @RequestBody BillingBalanceCreditRequest request) {
        platformAuthorizationService.requirePlatformCapability(principal.getName(), "platform.billing.balance.write");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(billingBalanceService.reverseBalance(companyId, request, principal.getName()));
    }
}
