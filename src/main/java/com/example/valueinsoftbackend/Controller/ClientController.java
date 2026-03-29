package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.Model.Request.CreateClientRequest;
import com.example.valueinsoftbackend.Service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping(path = "/{companyId}/getClientByPhone/{phone}/{bid}")
    public ResponseEntity<ArrayList<Client>> getClientByPhone(
            @PathVariable("phone") String phone,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid
    ) {
        return clientService.getClientByPhone(companyId, phone, bid);
    }

    @GetMapping(path = "/{companyId}/getLatestClients/{max}/{bid}")
    public ArrayList<Client> getLastClients(
            @PathVariable("max") @Positive int max,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid
    ) {
        return clientService.getLatestClients(companyId, max, bid);
    }

    @GetMapping(path = "/{companyId}/getClientsByName/{name}/{bid}")
    public ResponseEntity<ArrayList<Client>> getClientsByName(
            @PathVariable("name") String name,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid
    ) {
        return clientService.getClientsByName(companyId, name, bid);
    }

    @GetMapping(path = "/{companyId}/getClientsById/{cid}/{bid}")
    public Client getClientsById(
            @PathVariable("cid") @Positive int clientId,
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid
    ) {
        return clientService.getClientById(companyId, bid, clientId);
    }

    @GetMapping(path = "/{companyId}/{bid}/getCurrentYearClients")
    public HashMap<String, ArrayList<String>> getClientsByYear(
            @PathVariable("companyId") @Positive int companyId,
            @PathVariable("bid") @PositiveOrZero int bid
    ) {
        return clientService.getClientsByYear(companyId, bid);
    }

    @PostMapping("/{companyId}/AddClient")
    public ResponseEntity<Map<String, String>> newUser(
            @Valid @RequestBody CreateClientRequest body,
            @PathVariable("companyId") @Positive int companyId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(clientService.addClient(companyId, body));
    }
}
