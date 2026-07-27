package com.example.valueinsoftbackend.Model.PlatformAdmin;

import java.util.List;

/**
 * Tenant-free access projection for the authenticated platform operator.
 */
public record PlatformSessionAccessResponse(
        String userName,
        String role,
        List<String> capabilityKeys,
        List<String> moduleIds
) {
}
