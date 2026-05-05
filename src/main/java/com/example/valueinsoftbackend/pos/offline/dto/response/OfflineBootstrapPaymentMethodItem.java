package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineBootstrapPaymentMethodItem(
        String methodCode,
        String name,
        Boolean active,
        Boolean requiresReference
) {
}
