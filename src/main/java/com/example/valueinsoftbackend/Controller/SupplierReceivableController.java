package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Service.openitems.SupplierReceivableService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier-receivables")
public class SupplierReceivableController {
    private final SupplierReceivableService service;
    private final AuthorizationService authorization;

    public SupplierReceivableController(SupplierReceivableService service, AuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/{companyId}/{branchId}/{supplierId}/receipts")
    public ResponseEntity<Map<String, Object>> collect(@PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId, @PathVariable @Positive int supplierId,
            @Valid @RequestBody PaymentRequest request, Principal principal) {
        authorization.assertAuthenticatedCapability(principal.getName(), companyId, branchId, "suppliers.openitems.allocate");
        long receiptId = service.collectPayment(companyId, branchId, supplierId, request.amount(),
                request.currencyCode(), request.paymentMethod(), request.idempotencyKey(), principal.getName());
        return ResponseEntity.ok(Map.of("receiptId", receiptId, "status", "POSTED"));
    }

    public record PaymentRequest(@Positive BigDecimal amount, @NotBlank String currencyCode,
                                 @NotBlank String paymentMethod, @NotBlank String idempotencyKey) {}
}
