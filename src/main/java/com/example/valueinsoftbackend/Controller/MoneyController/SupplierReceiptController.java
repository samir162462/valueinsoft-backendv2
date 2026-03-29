package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Model.Request.SupplierReceiptCreateRequest;
import com.example.valueinsoftbackend.Service.SupplierReceiptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/SR")
public class SupplierReceiptController {

    private final SupplierReceiptService supplierReceiptService;

    public SupplierReceiptController(SupplierReceiptService supplierReceiptService) {
        this.supplierReceiptService = supplierReceiptService;
    }

    @RequestMapping(value = "/retrieve/{companyId}/{supplierId}", method = RequestMethod.GET)
    public ResponseEntity<Object> supplierReceipts(@PathVariable @Positive int companyId, @PathVariable @Positive int supplierId) {
        return ResponseEntity.ok(supplierReceiptService.getSupplierReceipts(companyId, supplierId));
    }

    @RequestMapping(value = "/add/{companyId}", method = RequestMethod.POST)
    public ResponseEntity<Object> addSupplierReceipts(@PathVariable @Positive int companyId,
                                                      @Valid @RequestBody SupplierReceiptCreateRequest supplierReceipt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierReceiptService.addSupplierReceipt(companyId, supplierReceipt));
    }
}
