package com.example.valueinsoftbackend.pos.offline.model;

import com.example.valueinsoftbackend.pos.offline.enums.PosClientType;
import com.example.valueinsoftbackend.pos.offline.enums.PosDeviceStatus;

import java.time.Instant;

/**
 * Row model for the pos_device table.
 * No JPA annotations — used with JdbcTemplate RowMapper.
 */
public record PosDeviceModel(
        Long id,
        Long companyId,
        Long branchId,
        String deviceCode,
        String deviceName,
        PosClientType clientType,
        String platform,
        PosDeviceStatus status,
        boolean allowedOffline,
        int maxOfflineHours,
        String appVersion,
        Instant lastHeartbeatAt,
        Long registeredBy,
        Instant createdAt,
        Instant updatedAt
) {}
