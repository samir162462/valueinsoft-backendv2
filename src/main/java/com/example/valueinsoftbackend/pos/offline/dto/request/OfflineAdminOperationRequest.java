package com.example.valueinsoftbackend.pos.offline.dto.request;

public record OfflineAdminOperationRequest(String reason, Boolean force) {
    public String normalizedReason() {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    public boolean forceRequested() {
        return Boolean.TRUE.equals(force);
    }
}
