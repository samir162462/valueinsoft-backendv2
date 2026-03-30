package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.Model.Request.CreateClientRequest;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.Service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.security.Principal;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/Client")
public class ClientController {

    private final ClientService clientService;
    private final AuthorizationService authorizationService;

    public ClientController(ClientService clientService, AuthorizationService authorizationService) {
        this.clientService = clientService;
        this.authorizationService = authorizationService;
    }

    @GetMapping(path = "/{companyId}/getClientByPhone/{phone}/{bid}")
    public ResponseEntity<ArrayList<Client>> getClientByPhone(
            @PathVariable("phone") String phone,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                bid > 0 ? bid : null,
                "clients.account.read"
        );
        return clientService.getClientByPhone(companyId, phone, bid);
    }

    @GetMapping(path = "/{companyId}/getLatestClients/{max}/{bid}")
    public ArrayList<Client> getLastClients(
            @PathVariable("max") @Positive int max,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                bid > 0 ? bid : null,
                "clients.account.read"
        );
        return clientService.getLatestClients(companyId, max, bid);
    }

    @GetMapping(path = "/{companyId}/getClientsByName/{name}/{bid}")
    public ResponseEntity<ArrayList<Client>> getClientsByName(
            @PathVariable("name") String name,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                bid > 0 ? bid : null,
                "clients.account.read"
        );
        return clientService.getClientsByName(companyId, name, bid);
    }

    @GetMapping(path = "/{companyId}/getClientsById/{cid}/{bid}")
    public Client getClientsById(
            @PathVariable("cid") @Positive int clientId,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                bid > 0 ? bid : null,
                "clients.account.read"
        );
        return clientService.getClientById(companyId, bid, clientId);
    }

    @GetMapping(path = "/{companyId}/{bid}/getCurrentYearClients")
    public HashMap<String, ArrayList<String>> getClientsByYear(
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                bid > 0 ? bid : null,
                "clients.account.read"
        );
        return clientService.getClientsByYear(companyId, bid);
    }

    @PostMapping("/{companyId}/AddClient")
    public ResponseEntity<Map<String, String>> newUser(
            @Valid @RequestBody CreateClientRequest body,
            @PathVariable("companyId") @Positive int companyId,
            Principal principal
    ) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                body.getBranchId() > 0 ? body.getBranchId() : null,
                "clients.account.create"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(clientService.addClient(companyId, body));
    }
}
