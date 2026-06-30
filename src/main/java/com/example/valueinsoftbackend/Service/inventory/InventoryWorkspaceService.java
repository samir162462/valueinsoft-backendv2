package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryAnalysisQueryGateway;
import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryProductQueryGateway;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryProductAssignRequest;
import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.DbInventoryWorkspaceCommandGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class InventoryWorkspaceService {

    private final InventoryProductQueryGateway inventoryProductQueryGateway;
    private final InventoryAnalysisQueryGateway inventoryAnalysisQueryGateway;
    private final InventoryPresetService inventoryPresetService;
    private final DbInventoryWorkspaceCommandGateway commandGateway;

    public InventoryWorkspaceService(InventoryProductQueryGateway inventoryProductQueryGateway,
                                     InventoryAnalysisQueryGateway inventoryAnalysisQueryGateway,
                                     InventoryPresetService inventoryPresetService,
                                     DbInventoryWorkspaceCommandGateway commandGateway) {
        this.inventoryProductQueryGateway = inventoryProductQueryGateway;
        this.inventoryAnalysisQueryGateway = inventoryAnalysisQueryGateway;
        this.inventoryPresetService = inventoryPresetService;
        this.commandGateway = commandGateway;
    }

    public void assignExistingProductToBranch(String actorName, InventoryProductAssignRequest request) {
        commandGateway.assignProductToBranch(actorName, request.getCompanyId(), request.getBranchId(), request.getProductId(), request.getDefaultSupplierId());
    }

    public InventoryQuickFindResponse quickFind(String actorName, InventoryQuickFindRequest request) {
        return inventoryProductQueryGateway.quickFind(actorName, request);
    }

    public InventoryCatalogBrowseResponse browseCatalog(String actorName, InventoryCatalogBrowseRequest request) {
        return inventoryProductQueryGateway.browseCatalog(actorName, request);
    }

    public InventoryAnalysisResponse analyzeInventory(String actorName, InventoryAnalysisRequest request) {
        return inventoryAnalysisQueryGateway.analyzeInventory(actorName, request);
    }

    public ArrayList<InventoryPresetResponse> getPresets(String actorName, Integer companyId, Integer branchId) {
        return inventoryPresetService.getPresets(actorName, companyId, branchId);
    }

    public InventoryPresetResponse createPreset(String actorName, InventoryPresetCreateRequest request) {
        return inventoryPresetService.createPreset(actorName, request);
    }

    public InventoryPresetResponse updatePreset(String actorName, String presetId, InventoryPresetUpdateRequest request) {
        return inventoryPresetService.updatePreset(actorName, presetId, request);
    }

    public void deletePreset(String actorName, String presetId) {
        inventoryPresetService.deletePreset(actorName, presetId);
    }
}
