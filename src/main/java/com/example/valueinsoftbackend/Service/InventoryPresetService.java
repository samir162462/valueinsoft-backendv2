package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryPresetGateway;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class InventoryPresetService {

    private final InventoryPresetGateway inventoryPresetGateway;

    public InventoryPresetService(InventoryPresetGateway inventoryPresetGateway) {
        this.inventoryPresetGateway = inventoryPresetGateway;
    }

    public ArrayList<InventoryPresetResponse> getPresets(String actorName, Integer companyId, Integer branchId) {
        return inventoryPresetGateway.getPresets(actorName, companyId, branchId);
    }

    public InventoryPresetResponse createPreset(String actorName, InventoryPresetCreateRequest request) {
        // Resolve companyId from queryState or a default if not present
        Integer companyId = (Integer) request.getQueryState().get("companyId");
        if (companyId == null) {
            // Fallback or error? For now fallback to a safe default if available or throw
            throw new IllegalArgumentException("companyId must be present in queryState for preset creation");
        }
        return inventoryPresetGateway.createPreset(actorName, companyId, request);
    }

    public InventoryPresetResponse updatePreset(String actorName, String presetId, InventoryPresetUpdateRequest request) {
        return inventoryPresetGateway.updatePreset(actorName, presetId, request);
    }

    public void deletePreset(String actorName, String presetId) {
        inventoryPresetGateway.deletePreset(actorName, presetId);
    }
}
