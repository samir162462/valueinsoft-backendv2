package com.example.valueinsoftbackend.pos.offline.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request to update device heartbeat (last-seen / app-version).
 */
public record DeviceHeartbeatRequest(

        @NotNull(message = "companyId is required")
        @Positive(message = "companyId must be positive")
        Long companyId,

        @NotNull(message = "branchId is required")
        @Positive(message = "branchId must be positive")
        Long branchId,

        @NotBlank(message = "deviceCode is required")
        @Size(max = 100)
        String deviceCode,

        @Size(max = 50)
        String appVersion
) {}
