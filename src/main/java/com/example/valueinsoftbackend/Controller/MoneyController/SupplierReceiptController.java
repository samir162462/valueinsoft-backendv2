package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Model.Request.SupplierReceiptCreateRequest;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.SupplierReceiptService;
import com.example.valueinsoftbackend.notification.producer.NotificationPilotIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;

@RestController
@Validated
@RequestMapping("/SR")
public class SupplierReceiptController {

    private final SupplierReceiptService supplierReceiptService;
    private final AuthorizationService authorizationService;
    private NotificationPilotIntegrationService notificationPilotIntegrationService;

    public SupplierReceiptController(SupplierReceiptService supplierReceiptService,
                                     AuthorizationService authorizationService) {
        this.supplierReceiptService = supplierReceiptService;
        this.authorizationService = authorizationService;
    }

    @Autowired(required = false)
    void setNotificationPilotIntegrationService(
            NotificationPilotIntegrationService notificationPilotIntegrationService) {
        this.notificationPilotIntegrationService = notificationPilotIntegrationService;
    }

    @RequestMapping(value = "/retrieve/{companyId}/{supplierId}", method = RequestMethod.GET)
    public ResponseEntity<Object> supplierReceipts(@PathVariable @Positive int companyId,
                                                   @PathVariable @Positive int supplierId,
                                                   Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "finance.entry.read"
        );
        return ResponseEntity.ok(supplierReceiptService.getSupplierReceipts(companyId, supplierId));
    }

    @RequestMapping(value = "/add/{companyId}", method = RequestMethod.POST)
    public ResponseEntity<Object> addSupplierReceipts(@PathVariable @Positive int companyId,
                                                      @Valid @RequestBody SupplierReceiptCreateRequest supplierReceipt,
                                                      Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                supplierReceipt.getBranchId(),
                "finance.entry.create"
        );
        SupplierReceipt created = supplierReceiptService.addSupplierReceipt(companyId, supplierReceipt);
        if (notificationPilotIntegrationService != null) {
            notificationPilotIntegrationService.afterFinancePaymentSent(
                    companyId,
                    created.getBranchId(),
                    created.getSrId(),
                    "supplier_payment",
                    created.getAmountPaid(),
                    supplierReceipt.getCurrencyCode(),
                    principal.getName());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
