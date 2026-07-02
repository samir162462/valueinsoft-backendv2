package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentPreviewResponse;
import com.example.valueinsoftbackend.Service.billing.BillingInvoicePaymentService;
import com.example.valueinsoftbackend.Service.billing.BillingPaymentInitiationCheckoutHydrator;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/billing/invoices")
public class BillingInvoicePaymentController {

    private final BillingInvoicePaymentService billingInvoicePaymentService;
    private final BillingPaymentInitiationCheckoutHydrator checkoutHydrator;
    private final AuthorizationService authorizationService;
    private final BillingProperties billingProperties;

    public BillingInvoicePaymentController(BillingInvoicePaymentService billingInvoicePaymentService,
                                           BillingPaymentInitiationCheckoutHydrator checkoutHydrator,
                                           AuthorizationService authorizationService,
                                           BillingProperties billingProperties) {
        this.billingInvoicePaymentService = billingInvoicePaymentService;
        this.checkoutHydrator = checkoutHydrator;
        this.authorizationService = authorizationService;
        this.billingProperties = billingProperties;
    }

    @GetMapping("/{invoiceId}/payment-preview")
    public BillingPaymentPreviewResponse previewPayment(@PathVariable @Positive long invoiceId,
                                                        Principal principal) {
        assertBalanceFirstApisEnabled();
        assertCapability(invoiceId, principal, "company.settings.read");
        return billingInvoicePaymentService.previewPayment(invoiceId);
    }

    @PostMapping("/{invoiceId}/initiate-payment")
    public ResponseEntity<BillingPaymentInitiationResponse> initiatePayment(
            @PathVariable @Positive long invoiceId,
            @RequestBody(required = false) BillingPaymentInitiationRequest request,
            Principal principal) {
        assertBalanceFirstApisEnabled();
        assertCapability(invoiceId, principal, "company.settings.edit");
        BillingPaymentInitiationResponse response =
                checkoutHydrator.hydrateCheckoutUrl(billingInvoicePaymentService.initiatePayment(invoiceId, request, principal.getName()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{invoiceId}/payment-attempts/latest")
    public ResponseEntity<BillingPaymentAttemptSnapshot> latestPaymentAttempt(
            @PathVariable @Positive long invoiceId,
            Principal principal) {
        assertBalanceFirstApisEnabled();
        assertCapability(invoiceId, principal, "company.settings.read");
        BillingPaymentAttemptSnapshot attempt = billingInvoicePaymentService.latestPaymentAttempt(invoiceId);
        return attempt == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(attempt);
    }

    @PostMapping("/{invoiceId}/settle-from-balance")
    public BillingPaymentInitiationResponse settleFromBalance(
            @PathVariable @Positive long invoiceId,
            @RequestBody(required = false) BillingPaymentInitiationRequest request,
            Principal principal) {
        assertBalanceFirstApisEnabled();
        assertCapability(invoiceId, principal, "company.settings.edit");
        return billingInvoicePaymentService.settleFromBalance(invoiceId, request);
    }

    private void assertBalanceFirstApisEnabled() {
        if (!billingProperties.isBalanceFirstPaymentApisEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "BILLING_BALANCE_FIRST_APIS_DISABLED",
                    "Balance-first billing payment APIs are disabled by rollout configuration"
            );
        }
    }

    private void assertCapability(long invoiceId, Principal principal, String capabilityKey) {
        BillingInvoicePaymentContext context = billingInvoicePaymentService.requireAuthorizationContext(invoiceId);
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                context.getCompanyId(),
                context.getBranchId(),
                capabilityKey
        );
    }
}
