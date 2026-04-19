package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryProductQueryGateway;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import org.springframework.stereotype.Service;

@Service
public class InventoryQuickFindService {

    private final InventoryProductQueryGateway inventoryProductQueryGateway;

    public InventoryQuickFindService(InventoryProductQueryGateway inventoryProductQueryGateway) {
        this.inventoryProductQueryGateway = inventoryProductQueryGateway;
    }

    public InventoryQuickFindResponse quickFind(String actorName, InventoryQuickFindRequest request) {
        // Integration note:
        // Implement using the legacy product search sources from:
        // - backend_inventory_stage7/ProductService.java
        // - backend_inventory_stage7/ProductController.java
        // Exact match order should be:
        // barcode -> productId -> serial -> fuzzy name fallback.
        throw new UnsupportedOperationException(
                "InventoryQuickFindService is scaffolded. Wire InventoryProductQueryGateway to the real product repository in the runnable backend project."
        );
    }
}
