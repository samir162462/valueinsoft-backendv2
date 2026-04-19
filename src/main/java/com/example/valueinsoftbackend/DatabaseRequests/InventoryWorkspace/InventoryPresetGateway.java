package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.InventoryPresetResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetCreateRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryPresetUpdateRequest;

import java.util.ArrayList;

/**
 * Port interface for inventory preset persistence and ownership checks.
 *
 * Implement this in the runnable backend project with the final preset schema.
 */
public interface InventoryPresetGateway {
    ArrayList<InventoryPresetResponse> getPresets(String actorName, Integer companyId, Integer branchId);
    InventoryPresetResponse createPreset(String actorName, Integer companyId, InventoryPresetCreateRequest request);
    InventoryPresetResponse updatePreset(String actorName, String presetId, InventoryPresetUpdateRequest request);
    void deletePreset(String actorName, String presetId);
}
