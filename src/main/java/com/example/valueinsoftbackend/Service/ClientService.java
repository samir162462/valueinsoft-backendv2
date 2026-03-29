package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbClient;
import com.example.valueinsoftbackend.Model.Client;
import com.example.valueinsoftbackend.Model.Request.CreateClientRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class ClientService {

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final DbClient dbClient;

    public ClientService(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public ResponseEntity<ArrayList<Client>> getClientByPhone(int companyId, String phone, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbClient.getClientByPhoneNumberOrName(companyId, phone, null, null, null, branchId);
    }

    public ArrayList<Client> getLatestClients(int companyId, int max, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbClient.getLatestClients(companyId, max, branchId);
    }

    public ResponseEntity<ArrayList<Client>> getClientsByName(int companyId, String name, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbClient.getClientByPhoneNumberOrName(companyId, null, name, null, null, branchId);
    }

    public Client getClientById(int companyId, int branchId, int clientId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbClient.getClientById(companyId, branchId, clientId);
    }

    public HashMap<String, ArrayList<String>> getClientsByYear(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbClient.getClientsByYear(companyId, branchId);
    }

    public Map<String, String> addClient(int companyId, CreateClientRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String message = dbClient.addClient(
                companyId,
                request.getClientName().trim(),
                request.getClientPhone().trim(),
                request.getBranchId(),
                request.getGender().trim(),
                request.getDesc() == null ? "" : request.getDesc().trim()
        );

        String safeMessage = message == null ? "the user not added -> error in server!" : message;
        String lowerMessage = safeMessage.toLowerCase();
        String found = (lowerMessage.contains("exist") || lowerMessage.contains("taken")) ? "true" : "false";

        if ("true".equals(found)) {
            log.info("Rejected duplicate client create for company {} branch {} phone {}", companyId, request.getBranchId(), request.getClientPhone().trim());
        } else {
            log.info("Created client for company {} branch {} phone {}", companyId, request.getBranchId(), request.getClientPhone().trim());
        }

        Map<String, String> response = new HashMap<>();
        response.put("title", safeMessage);
        response.put("found", found);
        return response;
    }
}
