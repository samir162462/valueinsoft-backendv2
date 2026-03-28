package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMSupplierReceipt;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/SR")
public class SupplierReceiptController {

    private final DBMSupplierReceipt supplierReceiptRepository;

    public SupplierReceiptController(DBMSupplierReceipt supplierReceiptRepository) {
        this.supplierReceiptRepository = supplierReceiptRepository;
    }

    @RequestMapping(value = "/retrieve/{companyId}/{supplierId}", method = RequestMethod.GET)
    public ResponseEntity<Object> supplierReceipts(@PathVariable int companyId, @PathVariable int supplierId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierReceiptRepository.getSupplierReceipts(companyId, supplierId));
    }

    @RequestMapping(value = "/add/{companyId}", method = RequestMethod.POST)
    public ResponseEntity<Object> addSupplierReceipts(@PathVariable int companyId, @RequestBody SupplierReceipt supplierReceipt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierReceiptRepository.addSupplierReceipt(companyId, supplierReceipt));
    }
}
