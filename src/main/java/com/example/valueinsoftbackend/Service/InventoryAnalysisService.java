package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryAnalysisQueryGateway;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;
import org.springframework.stereotype.Service;

@Service
public class InventoryAnalysisService {

    private final InventoryAnalysisQueryGateway inventoryAnalysisQueryGateway;

    public InventoryAnalysisService(InventoryAnalysisQueryGateway inventoryAnalysisQueryGateway) {
        this.inventoryAnalysisQueryGateway = inventoryAnalysisQueryGateway;
    }

    public InventoryAnalysisResponse analyzeInventory(String actorName, InventoryAnalysisRequest request) {
        // Integration note:
        // Implement using movement-ledger queries derived from:
        // - backend_inventory_stage7_history/DbPosInventoryTransaction.java
        // - backend_inventory_stage6b/InventoryTransactionService.java
        throw new UnsupportedOperationException(
                "InventoryAnalysisService is scaffolded. Wire InventoryAnalysisQueryGateway to the real transaction repository in the runnable backend project."
        );
    }
}
