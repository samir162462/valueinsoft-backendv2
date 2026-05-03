package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.PosDeviceStatus;

import java.time.Instant;

/**
 * Response returned after a successful device heartbeat.
 */
public record DeviceHeartbeatResponse(
        Long deviceId,
        String deviceCode,
        PosDeviceStatus status,
        Boolean allowedOffline,
        Instant serverTime
) {}
