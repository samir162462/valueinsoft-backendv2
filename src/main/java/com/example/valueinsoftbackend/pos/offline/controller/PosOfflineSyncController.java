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
/**
 * Controller for handling POS offline synchronization operations.
 * This includes device registration, heartbeats, bootstrapping data, and managing offline sync uploads and status.
 */
@RestController
@Validated
@RequestMapping("/api/pos")
@Slf4j
public class PosOfflineSyncController {

    private final PosDeviceService deviceService;
    private final BootstrapDataService bootstrapDataService;
    private final PosOfflineSyncService syncService;
    private final AuthorizationService authorizationService;

    /**
     * Constructs a new PosOfflineSyncController with the required services.
     *
     * @param deviceService        the service for managing POS devices
     * @param bootstrapDataService the service for retrieving bootstrap data
     * @param syncService          the service for handling offline synchronization
     * @param authorizationService the service for handling authorization checks
     */
    public PosOfflineSyncController(PosDeviceService deviceService,
                                    BootstrapDataService bootstrapDataService,
                                    PosOfflineSyncService syncService,
                                    AuthorizationService authorizationService) {
        this.deviceService = deviceService;
        this.bootstrapDataService = bootstrapDataService;
        this.syncService = syncService;
        this.authorizationService = authorizationService;
    }

    /**
     * Registers a new POS device.
     *
     * @param request   the device registration request containing company, branch, and device details
     * @param principal the authenticated principal
     * @return a response entity containing the registration result
     */
    @PostMapping("/device/register")
    public ResponseEntity<RegisterPosDeviceResponse> registerDevice(
            @Valid @RequestBody RegisterPosDeviceRequest request,
            Principal principal) {
        String principalName = authorize(principal, request.companyId(), request.branchId(), "pos.device.register");
        log.info("Device registration request: companyId={}, branchId={}, code={}",
                request.companyId(), request.branchId(), request.deviceCode());
        return ResponseEntity.status(201).body(deviceService.registerDevice(request, principalName));
    }

    /**
     * Processes a heartbeat from a POS device to update its status.
     *
     * @param request   the heartbeat request containing company, branch, and device info
     * @param principal the authenticated principal
     * @return a response entity containing the heartbeat response
     */
    @PostMapping("/device/heartbeat")
    public ResponseEntity<DeviceHeartbeatResponse> heartbeat(
            @Valid @RequestBody DeviceHeartbeatRequest request,
            Principal principal) {
        String principalName = authorize(principal, request.companyId(), request.branchId(), "pos.device.heartbeat");
        return ResponseEntity.ok(deviceService.heartbeat(request, principalName));
    }

    /**
     * Retrieves bootstrap data for a POS device.
     *
     * @param companyId    the company ID
     * @param branchId     the branch ID
     * @param dataType     the type of data to retrieve (e.g., products, customers)
     * @param versionNo    optional version number for incremental updates
     * @param cursor       optional cursor for pagination
     * @param size         the number of records to retrieve (default 500)
     * @param principal    the authenticated principal
     * @return a response entity containing the requested bootstrap data
     */
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

    /**
     * Uploads offline synchronization data (e.g., offline orders).
     *
     * @param request   the sync upload request containing orders and metadata
     * @param principal the authenticated principal
     * @return a response entity indicating the upload was accepted
     */
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

    /**
     * Retrieves the status of a specific synchronization batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the synchronization batch ID
     * @param principal the authenticated principal
     * @return a response entity containing the sync status
     */
    @GetMapping("/offline-sync/status/{batchId}")
    public ResponseEntity<SyncStatusResponse> getSyncStatus(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long batchId,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId, "pos.offline.status");
        return ResponseEntity.ok(syncService.getSyncStatus(companyId, branchId, batchId, principalName));
    }

    /**
     * Retrieves errors associated with a specific synchronization batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the synchronization batch ID
     * @param cursor    optional cursor for pagination
     * @param size      the number of error records to retrieve (default 100)
     * @param principal the authenticated principal
     * @return a response entity containing the list of sync errors
     */
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

    /**
     * Retries the processing of a specific offline order that failed during synchronization.
     *
     * @param companyId            the company ID
     * @param branchId             the branch ID
     * @param offlineOrderImportId the ID of the offline order import record to retry
     * @param principal            the authenticated principal
     * @return a response entity containing the retry result
     */
    @PostMapping("/offline-sync/retry/{offlineOrderImportId}")
    public ResponseEntity<OfflineRetryResultResponse> retryOfflineOrder(
            @RequestParam @Positive Long companyId,
            @RequestParam @Positive Long branchId,
            @PathVariable @Positive Long offlineOrderImportId,
            Principal principal) {
        String principalName = authorize(principal, companyId, branchId, "pos.offline.retry");
        return ResponseEntity.ok(syncService.retryOfflineOrder(companyId, branchId, offlineOrderImportId, principalName));
    }

    /**
     * Authorizes the current request by checking the principal and required capability.
     *
     * @param principal  the authenticated principal
     * @param companyId  the company ID
     * @param branchId   the branch ID
     * @param capability the required capability for the operation
     * @return the principal name if authorized
     * @throws ApiException if authentication fails or authorization is denied
     */
    private String authorize(Principal principal, Long companyId, Long branchId, String capability) {
        String principalName = principalName(principal);
        authorizationService.assertAuthenticatedCapability(
                principalName,
                toInteger(companyId, "companyId"),
                toInteger(branchId, "branchId"),
                capability);
        return principalName;
    }

    /**
     * Extracts the principal name and validates authentication.
     *
     * @param principal the principal to check
     * @return the principal name
     * @throws ApiException if the principal is null or has no name
     */
    private String principalName(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return principal.getName();
    }

    /**
     * Safely converts a Long value to an Integer for compatibility with internal services.
     *
     * @param value     the long value to convert
     * @param fieldName the name of the field (used for error reporting)
     * @return the integer value
     * @throws ApiException if the value is out of range for an Integer
     */
    private Integer toInteger(Long value, String fieldName) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_ACCESS", fieldName + " is out of range");
        }
    }
}
