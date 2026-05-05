package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineBootstrapCashierPermissionItem(
        String capabilityKey,
        Boolean allowed
) {
}
