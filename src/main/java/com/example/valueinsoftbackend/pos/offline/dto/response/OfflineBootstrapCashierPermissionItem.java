package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineBootstrapCashierPermissionItem(
        String capability,
        Boolean allowed
) {
}
