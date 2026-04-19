package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryProductQueryGateway;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import org.springframework.stereotype.Service;

@Service
public class InventoryCatalogBrowseService {

    private final InventoryProductQueryGateway inventoryProductQueryGateway;

    public InventoryCatalogBrowseService(InventoryProductQueryGateway inventoryProductQueryGateway) {
        this.inventoryProductQueryGateway = inventoryProductQueryGateway;
    }

    public InventoryCatalogBrowseResponse browseCatalog(String actorName, InventoryCatalogBrowseRequest request) {
        // Integration note:
        // Implement with full server-side filtering and pagination.
        // Do not preserve the current client-side businessLine/template filtering behavior.
        // Use staged legacy sources:
        // - backend_inventory_stage7/ProductService.java
        // - legacy allData / dir / comName search logic
        throw new UnsupportedOperationException(
                "InventoryCatalogBrowseService is scaffolded. Wire InventoryProductQueryGateway to the real browse query implementation in the runnable backend project."
        );
    }
}
