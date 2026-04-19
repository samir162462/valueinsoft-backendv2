package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;

/**
 * Port interface for movement-ledger and analysis queries.
 *
 * Implement this in the runnable backend project by adapting the real
 * inventory transaction repository and ledger SQL.
 */
public interface InventoryAnalysisQueryGateway {
    InventoryAnalysisResponse analyzeInventory(String actorName, InventoryAnalysisRequest request);
}
