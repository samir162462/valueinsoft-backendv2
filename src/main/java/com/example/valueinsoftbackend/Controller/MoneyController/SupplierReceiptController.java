/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MoneyController;


import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMSupplierReceipt;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/SR")
@CrossOrigin("*")
public class SupplierReceiptController {

    @RequestMapping(value = "/retrieve/{companyId}/{supplierId}",method = RequestMethod.GET)
    public ResponseEntity<Object> supplierReceipts(@PathVariable int companyId, @PathVariable int supplierId  ) throws Exception
    {
        System.out.println(supplierId);
        return DBMSupplierReceipt.getSupplierReceipts(companyId,supplierId);

    }
    @RequestMapping(value = "/add/{companyId}",method = RequestMethod.POST)
    public ResponseEntity<Object> addSupplierReceipts(@PathVariable int companyId, @RequestBody SupplierReceipt supplierReceipt  ) throws Exception
    {
        return DBMSupplierReceipt.AddSupplierReceipt(companyId,supplierReceipt);

    }

}
