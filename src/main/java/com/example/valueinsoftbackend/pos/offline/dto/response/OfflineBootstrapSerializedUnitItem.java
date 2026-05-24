package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineBootstrapSerializedUnitItem(
        Long productUnitId,
        Long productId,
        String trackingType,
        String unitIdentifier,
        String imei,
        String serialNumber,
        String status
) {
}
