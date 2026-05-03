package com.example.valueinsoftbackend.pos.offline.controller;

import com.example.valueinsoftbackend.pos.offline.dto.request.DeviceHeartbeatRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineSyncUploadRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.RegisterPosDeviceRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.*;
import com.example.valueinsoftbackend.pos.offline.service.BootstrapDataService;
import com.example.valueinsoftbackend.pos.offline.service.PosDeviceService;
import com.example.valueinsoftbackend.pos.offline.service.PosOfflineSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for POS offline sync operations.
 *
 * TODO: Add security annotations once the auth strategy for device APIs is decided.
 *       Options include:
 *         - Device-level JWT tokens (separate from user JWT)
 *         - API key per device
 *         - Standard user JWT with device claim
 */
@RestController
@Validated
@RequestMapping("/api/pos")
@Slf4j
public class PosOfflineSyncController {

    private final PosDeviceService deviceService;
    private final BootstrapDataService bootstrapDataService;
    private final PosOfflineSyncService syncService;

    public PosOfflineSyncController(PosDeviceService deviceService,
                                     BootstrapDataService bootstrapDataService,
                                     PosOfflineSyncService syncService) {
        this.deviceService = deviceService;
        this.bootstrapDataService = bootstrapDataService;
        this.syncService = syncService;
    }

    // -------------------------------------------------------
    // Device Management
    // -------------------------------------------------------

    @PostMapping("/device/register")
    public ResponseEntity<RegisterPosDeviceResponse> registerDevice(
            @Valid @RequestBody RegisterPosDeviceRequest request) {
        // TODO: Add @PreAuthorize or capability check for device registration
        log.info("Device registration request: companyId={}, branchId={}, code={}",
                request.companyId(), request.branchId(), request.deviceCode());
        return ResponseEntity.status(201).body(deviceService.registerDevice(request));
    }

    @PostMapping("/device/heartbeat")
    public ResponseEntity<DeviceHeartbeatResponse> heartbeat(
            @Valid @RequestBody DeviceHeartbeatRequest request) {
        // TODO: Add device authentication
        return ResponseEntity.ok(deviceService.heartbeat(request));
    }

    // -------------------------------------------------------
    // Bootstrap Data (offline cache)
    // -------------------------------------------------------

    @GetMapping("/bootstrap-data")
    public ResponseEntity<BootstrapDataResponse> getBootstrapData(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @RequestParam String dataType,
            @RequestParam(required = false) Long versionNo,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") Integer size) {
        // TODO: Add device authentication / capability check
        return ResponseEntity.ok(bootstrapDataService.getBootstrapData(
                companyId, branchId, dataType, versionNo, cursor, size));
    }

    // -------------------------------------------------------
    // Offline Sync Upload & Status
    // -------------------------------------------------------

    @PostMapping("/offline-sync/upload")
    public ResponseEntity<OfflineSyncUploadResponse> uploadOfflineSync(
            @Valid @RequestBody OfflineSyncUploadRequest request) {
        // TODO: Add device authentication
        log.info("Offline sync upload: companyId={}, branchId={}, deviceId={}, batchId={}, orders={}",
                request.companyId(), request.branchId(), request.deviceId(),
                request.clientBatchId(), request.orders().size());
        return ResponseEntity.status(202).body(syncService.uploadOfflineSync(request));
    }

    @GetMapping("/offline-sync/status/{batchId}")
    public ResponseEntity<SyncStatusResponse> getSyncStatus(
            @PathVariable @Positive Long batchId) {
        // TODO: Add authorization — caller must own this batch
        return ResponseEntity.ok(syncService.getSyncStatus(batchId));
    }

    @GetMapping("/offline-sync/errors/{batchId}")
    public ResponseEntity<List<SyncErrorResponse>> getSyncErrors(
            @PathVariable @Positive Long batchId) {
        // TODO: Add authorization
        return ResponseEntity.ok(syncService.getSyncErrors(batchId));
    }

    @PostMapping("/offline-sync/retry/{offlineOrderImportId}")
    public ResponseEntity<OfflineOrderSyncResult> retryOfflineOrder(
            @PathVariable @Positive Long offlineOrderImportId) {
        // TODO: Add authorization — only managers should be able to retry
        return ResponseEntity.ok(syncService.retryOfflineOrder(offlineOrderImportId));
    }
}
