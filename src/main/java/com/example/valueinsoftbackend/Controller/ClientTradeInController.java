package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.ClientTradeInPaymentRequest;
import com.example.valueinsoftbackend.Service.client.ClientTradeInService;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Client-as-seller (trade-in) endpoints: products a client sold to the shop,
 * the payable owed to them, payments, and the seller statement.
 * The authoritative receiving transaction remains
 * POST /api/inventory/products/receipts with acquisitionSource=CLIENT_TRADE_IN.
 */
@RestController
@Validated
@RequestMapping("/api/clients/trade-ins")
public class ClientTradeInController {

    private final ClientTradeInService clientTradeInService;
    private final AuthorizationService authorizationService;

    public ClientTradeInController(ClientTradeInService clientTradeInService,
                                   AuthorizationService authorizationService) {
        this.clientTradeInService = clientTradeInService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{companyId}/{clientId}/summary")
    public Map<String, Object> summary(@PathVariable @Positive int companyId,
                                       @PathVariable @Positive int clientId,
                                       Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, null, "clients.tradein.view");
        return clientTradeInService.getSummary(companyId, clientId);
    }

    @GetMapping("/{companyId}/{clientId}")
    public Map<String, Object> tradeIns(@PathVariable @Positive int companyId,
                                        @PathVariable @Positive int clientId,
                                        @RequestParam(value = "page", defaultValue = "0") int page,
                                        @RequestParam(value = "size", defaultValue = "20") int size,
                                        Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, null, "clients.tradein.view");
        return clientTradeInService.listTradeIns(companyId, clientId, page, size);
    }

    @GetMapping("/{companyId}/{clientId}/payments")
    public Map<String, Object> payments(@PathVariable @Positive int companyId,
                                        @PathVariable @Positive int clientId,
                                        @RequestParam(value = "page", defaultValue = "0") int page,
                                        @RequestParam(value = "size", defaultValue = "20") int size,
                                        Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, null, "clients.tradein.view");
        return clientTradeInService.listPayments(companyId, clientId, page, size);
    }

    @GetMapping("/{companyId}/{clientId}/statement")
    public Map<String, Object> statement(@PathVariable @Positive int companyId,
                                         @PathVariable @Positive int clientId,
                                         Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, null, "clients.statement.view");
        return clientTradeInService.getStatement(companyId, clientId);
    }

    @PostMapping("/{companyId}/payments")
    public ResponseEntity<Map<String, Object>> payClient(@PathVariable @Positive int companyId,
                                                         @Valid @RequestBody ClientTradeInPaymentRequest body,
                                                         Principal principal) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(), companyId, body.getBranchId(), "clients.payment.create");
        Map<String, Object> response = clientTradeInService.payClient(companyId, principal.getName(), body);
        boolean replay = Boolean.TRUE.equals(response.get("idempotentReplay"));
        return ResponseEntity.status(replay ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
