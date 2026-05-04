package com.example.valueinsoftbackend.pos.offline.dto.response;

public record OfflineAdminErrorCodeCount(
        String errorCode,
        int count
) {
}
