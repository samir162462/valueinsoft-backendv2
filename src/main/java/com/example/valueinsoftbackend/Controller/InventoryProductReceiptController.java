package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.AcquisitionSource;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptOperationMode;
import com.example.valueinsoftbackend.Model.Request.InventoryReceipt.ProductReceiptRequest;
import com.example.valueinsoftbackend.Model.Response.InventoryReceipt.ProductReceiptResponse;
import com.example.valueinsoftbackend.Service.inventory.InventoryProductReceiptService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/inventory/products")
public class InventoryProductReceiptController {

    private final InventoryProductReceiptService receiptService;
    private final AuthorizationService authorizationService;

    public InventoryProductReceiptController(InventoryProductReceiptService receiptService,
                                             AuthorizationService authorizationService) {
        this.receiptService = receiptService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/receipts")
    public ResponseEntity<ProductReceiptResponse> receiveProduct(@Valid @RequestBody ProductReceiptRequest request,
                                                                 Principal principal) {
        if (request.getOperationMode() == ProductReceiptOperationMode.CREATE_PRODUCT_AND_RECEIVE) {
            authorizationService.assertAuthenticatedCapability(
                    principal.getName(),
                    request.getCompanyId(),
                    request.getBranchId(),
                    "inventory.item.create");
        }
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                request.getCompanyId(),
                request.getBranchId(),
                "inventory.adjustment.create");
        boolean clientTradeIn = request.getReceipt() != null
                && AcquisitionSource.defaultIfNull(request.getReceipt().getAcquisitionSource()).isClientTradeIn();
        if (clientTradeIn) {
            authorizationService.assertAuthenticatedCapability(
                    principal.getName(),
                    request.getCompanyId(),
                    request.getBranchId(),
                    "clients.tradein.create");
        } else {
            authorizationService.assertAuthenticatedCapability(
                    principal.getName(),
                    request.getCompanyId(),
                    request.getBranchId(),
                    "suppliers.account.edit");
        }
        ProductReceiptResponse response = receiptService.receiveProduct(principal.getName(), request);
        return ResponseEntity.status(response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}

