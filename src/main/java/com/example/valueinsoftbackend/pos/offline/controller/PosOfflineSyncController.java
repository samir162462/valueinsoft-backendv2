package com.example.valueinsoftbackend.pos.offline.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.pos.offline.dto.request.DeviceHeartbeatRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineSyncUploadRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.RegisterPosDeviceRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.BootstrapDataResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.DeviceHeartbeatResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineRetryResultResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineSyncUploadResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.RegisterPosDeviceResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorListResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncStatusResponse;
import com.example.valueinsoftbackend.pos.offline.service.BootstrapDataService;
import com.example.valueinsoftbackend.pos.offline.service.PosDeviceService;
import com.example.valueinsoftbackend.pos.offline.service.PosOfflineSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
@RestController
@Validated
@RequestMapping("/api/pos")
@Slf4j
public class PosOfflineSyncController {

    private final PosDeviceService deviceService;
    private final BootstrapDataService bootstrapDataService;
    private final PosOfflineSyncService syncService;
    private final AuthorizationService authorizationService;

    public PosOfflineSyncController(PosDeviceService deviceService,
                                    BootstrapDataService bootstrapDataService,
                                    PosOfflineSyncService syncService,
                                    AuthorizationService authorizationService) {
        this.deviceService = deviceService;
        this.bootstrapDataService = bootstrapDataService;
        this.syncService = syncService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/device/register")
    public ResponseEntity<RegisterPosDeviceResponse> registerDevice(
            @Valid @RequestBody RegisterPosDeviceRequest request,
            Principal principal) {
        String principalName = authorize(principal, request.companyId(), request.branchId(), "pos.device.register");
        log.info("Device registration request: companyId={}, branchId={}, code={}",
                request.companyId(), request.branchId(), request.deviceCode());
        return ResponseEntity.status(201).body(deviceService.registerDevice(request, principalName));
    }

    @PostMapping("/device/heartbeat")
    public ResponseEntity<DeviceHeartbeatResponse> heartbeat(
            @Valid @RequestBody DeviceHeartbeatRequest request,
            Principal principal) {
        String principalName = authorize(principal, request.companyId(), request.branchId(), "pos.device.heartbeat");
        return ResponseEntity.ok(deviceService.heartbeat(request, principalName));
    }

    @GetMapping("/bootstrap-data")
    public ResponseEntity<BootstrapDataResponse> getBootstrapData(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @RequestParam String dataType,
            @RequestParam(required = false) Long versionNo,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "500") Integer size,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId, "pos.bootstrap.read");
        return ResponseEntity.ok(bootstrapDataService.getBootstrapData(
                companyId, branchId, dataType, versionNo, cursor, size, principalName));
    }

    @PostMapping("/offline-sync/upload")
    public ResponseEntity<OfflineSyncUploadResponse> uploadOfflineSync(
            @Valid @RequestBody OfflineSyncUploadRequest request,
            Principal principal) {
        String principalName = authorize(principal, request.companyId(), request.branchId(), "pos.offline.sync");
        log.info("Offline sync upload: companyId={}, branchId={}, deviceId={}, batchId={}, orders={}",
                request.companyId(), request.branchId(), request.deviceId(),
                request.clientBatchId(), request.orders().size());
        return ResponseEntity.status(202).body(syncService.uploadOfflineSync(request, principalName));
    }

    @GetMapping("/offline-sync/status/{batchId}")
    public ResponseEntity<SyncStatusResponse> getSyncStatus(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId, "pos.offline.status");
        return ResponseEntity.ok(syncService.getSyncStatus(companyId, branchId, batchId, principalName));
    }

    @GetMapping("/offline-sync/errors/{batchId}")
    public ResponseEntity<SyncErrorListResponse> getSyncErrors(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") Integer size,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId, "pos.offline.errors");
        return ResponseEntity.ok(syncService.getSyncErrors(companyId, branchId, batchId, cursor, size, principalName));
    }

    @PostMapping("/offline-sync/retry/{offlineOrderImportId}")
    public ResponseEntity<OfflineRetryResultResponse> retryOfflineOrder(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long offlineOrderImportId,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId, "pos.offline.retry");
        return ResponseEntity.ok(syncService.retryOfflineOrder(companyId, branchId, offlineOrderImportId, principalName));
    }

    private String authorize(Principal principal, Long companyId, Long branchId, String capability) {
        String principalName = principalName(principal);
        authorizationService.assertAuthenticatedCapability(
                principalName,
                toInteger(companyId, "companyId"),
                toInteger(branchId, "branchId"),
                capability);
        return principalName;
    }

    private String principalName(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return principal.getName();
    }

    private Integer toInteger(Long value, String fieldName) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_ACCESS", fieldName + " is out of range");
        }
    }
}
