package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineBootstrapPaymentMethodItem(
        String code,
        String name,
        Boolean active,
        Boolean requiresReference
) {
}
