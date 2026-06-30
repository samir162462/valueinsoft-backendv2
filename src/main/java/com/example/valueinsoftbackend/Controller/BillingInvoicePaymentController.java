package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationRequest;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentInitiationResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentPreviewResponse;
import com.example.valueinsoftbackend.Service.billing.BillingInvoicePaymentService;
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
    private final AuthorizationService authorizationService;

    public BillingInvoicePaymentController(BillingInvoicePaymentService billingInvoicePaymentService,
                                           AuthorizationService authorizationService) {
        this.billingInvoicePaymentService = billingInvoicePaymentService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{invoiceId}/payment-preview")
    public BillingPaymentPreviewResponse previewPayment(@PathVariable @Positive long invoiceId,
                                                        Principal principal) {
        assertCapability(invoiceId, principal, "company.settings.read");
        return billingInvoicePaymentService.previewPayment(invoiceId);
    }

    @PostMapping("/{invoiceId}/initiate-payment")
    public ResponseEntity<BillingPaymentInitiationResponse> initiatePayment(
            @PathVariable @Positive long invoiceId,
            @RequestBody(required = false) BillingPaymentInitiationRequest request,
            Principal principal) {
        assertCapability(invoiceId, principal, "company.settings.edit");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(billingInvoicePaymentService.initiatePayment(invoiceId, request));
    }

    @PostMapping("/{invoiceId}/settle-from-balance")
    public BillingPaymentInitiationResponse settleFromBalance(
            @PathVariable @Positive long invoiceId,
            @RequestBody(required = false) BillingPaymentInitiationRequest request,
            Principal principal) {
        assertCapability(invoiceId, principal, "company.settings.edit");
        return billingInvoicePaymentService.settleFromBalance(invoiceId, request);
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
