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

    public PosDeviceService(PosDeviceRepository deviceRepo, OfflinePosProperties props) {
        this.deviceRepo = deviceRepo;
        this.props = props;
    }

    /**
     * Register a new POS device or return existing if device_code already registered.
     */
    public RegisterPosDeviceResponse registerDevice(RegisterPosDeviceRequest request) {
        // Check if device already exists
        Optional<PosDeviceModel> existing = deviceRepo.findByCompanyBranchDeviceCode(
                request.companyId(), request.branchId(), request.deviceCode());

        if (existing.isPresent()) {
            PosDeviceModel d = existing.get();
            log.info("Device already registered: companyId={}, branchId={}, code={}",
                    request.companyId(), request.branchId(), request.deviceCode());
            return new RegisterPosDeviceResponse(
                    d.id(), d.deviceCode(), d.status(), d.allowedOffline(), d.maxOfflineHours());
        }

        // TODO: Validate that companyId and branchId exist in the system
        // TODO: Add security check — only authorized users can register devices

        Long deviceId = deviceRepo.insertDevice(
                request.companyId(), request.branchId(), request.deviceCode(),
                request.deviceName(), request.clientType(), request.platform(),
                request.appVersion(), null /* registeredBy — TODO: extract from security context */);

        log.info("New POS device registered: id={}, companyId={}, branchId={}, code={}",
                deviceId, request.companyId(), request.branchId(), request.deviceCode());

        return new RegisterPosDeviceResponse(
                deviceId, request.deviceCode(), PosDeviceStatus.ACTIVE,
                false, props.getMaxOfflineHoursDefault());
    }

    /**
     * Update device heartbeat and return current device status.
     */
    public DeviceHeartbeatResponse heartbeat(DeviceHeartbeatRequest request) {
        PosDeviceModel device = deviceRepo.findByCompanyBranchDeviceCode(
                        request.companyId(), request.branchId(), request.deviceCode())
                .orElseThrow(() -> new OfflineSyncException(
                        "DEVICE_NOT_REGISTERED",
                        "Device not registered: " + request.deviceCode()));

        deviceRepo.updateHeartbeat(device.id(), request.appVersion());

        return new DeviceHeartbeatResponse(
                device.id(), device.deviceCode(), device.status(),
                device.allowedOffline(), Instant.now());
    }

    /**
     * Validates that a device is eligible for offline sync.
     * Called before processing any sync upload.
     */
    public void validateDeviceForOfflineSync(Long companyId, Long branchId, Long deviceId) {
        PosDeviceModel device = deviceRepo.findById(deviceId)
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

        // TODO: Check that device.companyId and device.branchId match the request
        // TODO: Check offline window (max_offline_hours) against offlineStartedAt
    }
}
