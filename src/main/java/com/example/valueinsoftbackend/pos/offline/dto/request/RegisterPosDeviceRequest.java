package com.example.valueinsoftbackend.pos.offline.dto.request;

import com.example.valueinsoftbackend.pos.offline.enums.PosClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request to register a new POS device for a specific company/branch.
 */
public record RegisterPosDeviceRequest(

        @NotNull(message = "companyId is required")
        @Positive(message = "companyId must be positive")
        Long companyId,

        @NotNull(message = "branchId is required")
        @Positive(message = "branchId must be positive")
        Long branchId,

        @NotBlank(message = "deviceCode is required")
        @Size(max = 100, message = "deviceCode must not exceed 100 characters")
        String deviceCode,

        @Size(max = 200, message = "deviceName must not exceed 200 characters")
        String deviceName,

        @NotNull(message = "clientType is required")
        PosClientType clientType,

        @Size(max = 50, message = "platform must not exceed 50 characters")
        String platform,

        @Size(max = 50, message = "appVersion must not exceed 50 characters")
        String appVersion
) {}
