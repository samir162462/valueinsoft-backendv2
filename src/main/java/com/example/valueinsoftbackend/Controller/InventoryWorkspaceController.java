package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryAnalysisResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryCatalogBrowseResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryQuickFindResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import com.example.valueinsoftbackend.Service.InventoryWorkspaceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;

@RestController
@Validated
@RequestMapping("/api/inventory")
public class InventoryWorkspaceController {

    private final InventoryWorkspaceService inventoryWorkspaceService;

    public InventoryWorkspaceController(InventoryWorkspaceService inventoryWorkspaceService) {
        this.inventoryWorkspaceService = inventoryWorkspaceService;
    }

    @PostMapping("/quick-find")
    public InventoryQuickFindResponse quickFind(Principal principal,
                                                @Valid @RequestBody InventoryQuickFindRequest request) {
        return inventoryWorkspaceService.quickFind(principal.getName(), request);
    }

    @PostMapping("/catalog/search")
    public InventoryCatalogBrowseResponse browseCatalog(Principal principal,
                                                        @Valid @RequestBody InventoryCatalogBrowseRequest request) {
        return inventoryWorkspaceService.browseCatalog(principal.getName(), request);
    }

    @PostMapping("/analysis/search")
    public InventoryAnalysisResponse analyzeInventory(Principal principal,
                                                      @Valid @RequestBody InventoryAnalysisRequest request) {
        return inventoryWorkspaceService.analyzeInventory(principal.getName(), request);
    }

    @GetMapping("/presets")
    public ArrayList<InventoryPresetResponse> getPresets(Principal principal,
                                                         @RequestParam(required = false) Integer companyId,
                                                         @RequestParam(required = false) Integer branchId) {
        return inventoryWorkspaceService.getPresets(principal.getName(), companyId, branchId);
    }

    @PostMapping("/presets")
    public InventoryPresetResponse createPreset(Principal principal,
                                                @Valid @RequestBody InventoryPresetCreateRequest request) {
        return inventoryWorkspaceService.createPreset(principal.getName(), request);
    }

    @PutMapping("/presets/{presetId}")
    public InventoryPresetResponse updatePreset(Principal principal,
                                                @PathVariable String presetId,
                                                @Valid @RequestBody InventoryPresetUpdateRequest request) {
        return inventoryWorkspaceService.updatePreset(principal.getName(), presetId, request);
    }

    @DeleteMapping("/presets/{presetId}")
    public void deletePreset(Principal principal,
                             @PathVariable String presetId) {
        inventoryWorkspaceService.deletePreset(principal.getName(), presetId);
    }
}

