package com.example.valueinsoftbackend.pos.offline.dto.request;

import com.example.valueinsoftbackend.pos.offline.enums.PosClientType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/**
 * Request to upload a batch of offline orders for sync.
 */
public record OfflineSyncUploadRequest(

        @NotNull(message = "companyId is required")
        @Positive
        Long companyId,

        @NotNull(message = "branchId is required")
        @Positive
        Long branchId,

        @NotNull(message = "deviceId is required")
        @Positive
        Long deviceId,

        @NotNull(message = "cashierId is required")
        @Positive
        Long cashierId,

        @NotNull(message = "clientType is required")
        PosClientType clientType,

        @Size(max = 50)
        String platform,

        @Size(max = 50)
        String appVersion,

        @NotBlank(message = "clientBatchId is required")
        @Size(max = 150)
        String clientBatchId,

        Instant offlineStartedAt,

        Instant syncStartedAt,

        @NotEmpty(message = "orders cannot be empty")
        @Valid
        List<OfflineOrderRequest> orders
) {}
