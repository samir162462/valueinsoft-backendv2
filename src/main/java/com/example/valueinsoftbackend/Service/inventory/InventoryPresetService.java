package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace.InventoryPresetGateway;
import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class InventoryPresetService {

    private final InventoryPresetGateway inventoryPresetGateway;

    public InventoryPresetService(InventoryPresetGateway inventoryPresetGateway) {
        this.inventoryPresetGateway = inventoryPresetGateway;
    }

    public ArrayList<InventoryPresetResponse> getPresets(String actorName, Integer companyId, Integer branchId) {
        return inventoryPresetGateway.getPresets(actorName, companyId, branchId);
    }

    public InventoryPresetResponse createPreset(String actorName,
                                                Integer companyId,
                                                Integer branchId,
                                                InventoryPresetCreateRequest request) {
        LinkedHashMap<String, Object> queryState = request.getQueryState() == null
                ? new LinkedHashMap<>()
                : request.getQueryState();
        request.setQueryState(queryState);
        queryState.put("companyId", companyId);
        if (branchId == null) {
            queryState.remove("branchId");
        } else {
            queryState.put("branchId", branchId);
        }
        return inventoryPresetGateway.createPreset(actorName, companyId, branchId, request);
    }

    public InventoryPresetResponse updatePreset(String actorName,
                                                Integer companyId,
                                                String presetId,
                                                InventoryPresetUpdateRequest request) {
        LinkedHashMap<String, Object> queryState = request.getQueryState() == null
                ? new LinkedHashMap<>()
                : request.getQueryState();
        request.setQueryState(queryState);
        queryState.put("companyId", companyId);
        return inventoryPresetGateway.updatePreset(actorName, companyId, presetId, request);
    }

    public void deletePreset(String actorName, Integer companyId, String presetId) {
        inventoryPresetGateway.deletePreset(actorName, companyId, presetId);
    }
}
