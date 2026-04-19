package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class InventoryWorkspaceService {

    private final InventoryQuickFindService inventoryQuickFindService;
    private final InventoryCatalogBrowseService inventoryCatalogBrowseService;
    private final InventoryAnalysisService inventoryAnalysisService;
    private final InventoryPresetService inventoryPresetService;

    public InventoryWorkspaceService(InventoryQuickFindService inventoryQuickFindService,
                                     InventoryCatalogBrowseService inventoryCatalogBrowseService,
                                     InventoryAnalysisService inventoryAnalysisService,
                                     InventoryPresetService inventoryPresetService) {
        this.inventoryQuickFindService = inventoryQuickFindService;
        this.inventoryCatalogBrowseService = inventoryCatalogBrowseService;
        this.inventoryAnalysisService = inventoryAnalysisService;
        this.inventoryPresetService = inventoryPresetService;
    }

    public InventoryQuickFindResponse quickFind(String actorName, InventoryQuickFindRequest request) {
        return inventoryQuickFindService.quickFind(actorName, request);
    }

    public InventoryCatalogBrowseResponse browseCatalog(String actorName, InventoryCatalogBrowseRequest request) {
        return inventoryCatalogBrowseService.browseCatalog(actorName, request);
    }

    public InventoryAnalysisResponse analyzeInventory(String actorName, InventoryAnalysisRequest request) {
        return inventoryAnalysisService.analyzeInventory(actorName, request);
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
