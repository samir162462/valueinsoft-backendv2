package com.example.valueinsoftbackend.pos.offline.dto.response;

import com.example.valueinsoftbackend.pos.offline.enums.PosDeviceStatus;

/**
 * Response returned after successful device registration.
 */
public record RegisterPosDeviceResponse(
        Long deviceId,
        String deviceCode,
        PosDeviceStatus status,
        Boolean allowedOffline,
        Integer maxOfflineHours
) {}
