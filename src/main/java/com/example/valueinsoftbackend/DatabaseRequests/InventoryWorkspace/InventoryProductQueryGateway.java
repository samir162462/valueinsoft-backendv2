package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;

/**
 * Port interface for the inventory product query layer.
 *
 * Implement this in the runnable backend project by adapting the real
 * product repository and legacy search code currently represented by the
 * staged inventory backend files in this repository.
 */
public interface InventoryProductQueryGateway {
    InventoryQuickFindResponse quickFind(String actorName, InventoryQuickFindRequest request);
    InventoryCatalogBrowseResponse browseCatalog(String actorName, InventoryCatalogBrowseRequest request);
}
