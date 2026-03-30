/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Controller.MoneyController;

import com.example.valueinsoftbackend.Model.Request.CreateClientReceiptRequest;
import com.example.valueinsoftbackend.Model.Sales.ClientReceipt;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.ClientReceiptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/CR")
public class ClientReceiptController {

    private final ClientReceiptService clientReceiptService;
    private final AuthorizationService authorizationService;

    public ClientReceiptController(ClientReceiptService clientReceiptService,
                                   AuthorizationService authorizationService) {
        this.clientReceiptService = clientReceiptService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{clientId}")
    public ArrayList<ClientReceipt> clientReceipts(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int clientId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                null,
                "finance.entry.read"
        );
        return clientReceiptService.getClientReceipts(companyId, clientId);
    }

    @GetMapping("/{companyId}/{branchId}/{startTime}/{endTime}")
    public ArrayList<ClientReceipt> clientReceiptsByTime(
            @PathVariable @Positive int companyId,
            @PathVariable @Positive int branchId,
            @PathVariable String startTime,
            @PathVariable String endTime,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "finance.entry.read"
        );
        return clientReceiptService.getClientReceiptsByTime(companyId, branchId, startTime, endTime);
    }

    @PostMapping("/{companyId}")
    public String addClientReceipts(
            @Valid @RequestBody CreateClientReceiptRequest clientReceipt,
            @PathVariable @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                clientReceipt.getBranchId(),
                "finance.entry.create"
        );
        return clientReceiptService.addClientReceipt(companyId, clientReceipt);
    }
}
