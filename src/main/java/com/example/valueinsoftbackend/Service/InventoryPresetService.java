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
        throw new UnsupportedOperationException(
                "InventoryPresetService is scaffolded. Wire InventoryPresetGateway in the runnable backend project."
        );
    }

    public InventoryPresetResponse createPreset(String actorName, InventoryPresetCreateRequest request) {
        throw new UnsupportedOperationException(
                "InventoryPresetService is scaffolded. Wire InventoryPresetGateway in the runnable backend project."
        );
    }

    public InventoryPresetResponse updatePreset(String actorName, String presetId, InventoryPresetUpdateRequest request) {
        throw new UnsupportedOperationException(
                "InventoryPresetService is scaffolded. Wire InventoryPresetGateway in the runnable backend project."
        );
    }

    public void deletePreset(String actorName, String presetId) {
        throw new UnsupportedOperationException(
                "InventoryPresetService is scaffolded. Wire InventoryPresetGateway in the runnable backend project."
        );
    }
}
