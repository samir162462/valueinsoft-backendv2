package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.dto.request.DeviceHeartbeatRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.RegisterPosDeviceRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.DeviceHeartbeatResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.RegisterPosDeviceResponse;
import com.example.valueinsoftbackend.pos.offline.enums.PosDeviceStatus;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.PosDeviceModel;
import com.example.valueinsoftbackend.pos.offline.repository.PosDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
public class PosDeviceService {

    private final PosDeviceRepository deviceRepo;
    private final OfflinePosProperties props;
    private final AuditLogService auditLogService;

    public PosDeviceService(PosDeviceRepository deviceRepo,
                            OfflinePosProperties props,
                            AuditLogService auditLogService) {
        this.deviceRepo = deviceRepo;
        this.props = props;
        this.auditLogService = auditLogService;
    }

    public RegisterPosDeviceResponse registerDevice(RegisterPosDeviceRequest request, String principalName) {
        auditLogService.logSyncEvent(
                request.companyId(), request.branchId(),
                null, null, null, null,
                "DEVICE_REGISTRATION_REQUESTED",
                "Device registration requested by " + principalName + " for code " + request.deviceCode(),
                null);

        Optional<PosDeviceModel> existing = deviceRepo.findByCompanyBranchDeviceCode(
                request.companyId(), request.branchId(), request.deviceCode());

        if (existing.isPresent()) {
            PosDeviceModel d = existing.get();
            log.info("Device already registered: companyId={}, branchId={}, code={}",
                    request.companyId(), request.branchId(), request.deviceCode());
            auditLogService.logSyncEvent(
                    request.companyId(), request.branchId(),
                    null, null, d.id(), null,
                    "DEVICE_REGISTRATION_SUCCEEDED",
                    "Existing device registration returned to " + principalName + " for code " + request.deviceCode(),
                    null);
            return new RegisterPosDeviceResponse(
                    d.id(), d.deviceCode(), d.status(), d.allowedOffline(), d.maxOfflineHours());
        }

        // TODO: Validate that companyId and branchId exist in the system.
        // TODO: Resolve registered_by user id from principalName when user id lookup is available.
        Long deviceId = deviceRepo.insertDevice(
                request.companyId(), request.branchId(), request.deviceCode(),
                request.deviceName(), request.clientType(), request.platform(),
                request.appVersion(), null);

        log.info("New POS device registered: id={}, companyId={}, branchId={}, code={}",
                deviceId, request.companyId(), request.branchId(), request.deviceCode());
        auditLogService.logSyncEvent(
                request.companyId(), request.branchId(),
                null, null, deviceId, null,
                "DEVICE_REGISTRATION_SUCCEEDED",
                "Device registered by " + principalName + " for code " + request.deviceCode(),
                null);

        return new RegisterPosDeviceResponse(
                deviceId, request.deviceCode(), PosDeviceStatus.ACTIVE,
                false, props.getMaxOfflineHoursDefault());
    }

    public DeviceHeartbeatResponse heartbeat(DeviceHeartbeatRequest request, String principalName) {
        PosDeviceModel device = deviceRepo.findByCompanyBranchDeviceCode(
                        request.companyId(), request.branchId(), request.deviceCode())
                .orElseThrow(() -> new OfflineSyncException(
                        "DEVICE_NOT_REGISTERED",
                        "Device not registered: " + request.deviceCode()));

        deviceRepo.updateHeartbeat(request.companyId(), request.branchId(), device.id(), request.appVersion());
        auditLogService.logSyncEvent(
                request.companyId(), request.branchId(),
                null, null, device.id(), null,
                "DEVICE_HEARTBEAT_RECEIVED",
                "Device heartbeat received from " + request.deviceCode() + " by " + principalName,
                null);

        return new DeviceHeartbeatResponse(
                device.id(), device.deviceCode(), device.status(),
                device.allowedOffline(), Instant.now());
    }

    public void validateDeviceForOfflineSync(Long companyId, Long branchId, Long deviceId) {
        PosDeviceModel device = deviceRepo.findById(companyId, branchId, deviceId)
                .orElseThrow(() -> new OfflineSyncException(
                        "DEVICE_NOT_REGISTERED",
                        "Device not found: " + deviceId));

        if (device.status() == PosDeviceStatus.BLOCKED) {
            throw new OfflineSyncException("DEVICE_BLOCKED",
                    "Device is blocked: " + device.deviceCode());
        }

        if (device.status() == PosDeviceStatus.INACTIVE) {
            throw new OfflineSyncException("DEVICE_BLOCKED",
                    "Device is inactive: " + device.deviceCode());
        }

        if (!device.allowedOffline()) {
            throw new OfflineSyncException("DEVICE_OFFLINE_NOT_ALLOWED",
                    "Device is not allowed for offline sync: " + device.deviceCode());
        }

        // TODO: Check offline window (max_offline_hours) against offlineStartedAt.
    }
}
