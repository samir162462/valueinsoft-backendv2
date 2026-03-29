/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Model.Request.CreateClientReceiptRequest;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.Service.ClientReceiptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/CR")
public class ClientReceiptController {

    private final ClientReceiptService clientReceiptService;

    public ClientReceiptController(ClientReceiptService clientReceiptService) {
        this.clientReceiptService = clientReceiptService;
    }

    @GetMapping("/{companyId}/{clientId}")
    public ArrayList<ClientReceipt> clientReceipts(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId
    ) {
        return clientReceiptService.getClientReceipts(companyId, clientId);
    }

    @GetMapping("/{companyId}/{branchId}/{startTime}/{endTime}")
    public ArrayList<ClientReceipt> clientReceiptsByTime(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable String startTime,
            @PathVariable String endTime
    ) {
        return clientReceiptService.getClientReceiptsByTime(companyId, branchId, startTime, endTime);
    }

    @PostMapping("/{companyId}")
    public String addClientReceipts(
            @Valid @RequestBody CreateClientReceiptRequest clientReceipt,
            @PathVariable @Positive int companyId
    ) {
        return clientReceiptService.addClientReceipt(companyId, clientReceipt);
    }
}
