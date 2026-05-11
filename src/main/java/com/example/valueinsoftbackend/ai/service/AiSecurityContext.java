package com.example.valueinsoftbackend.ai.service;

import java.util.Set;

public record AiSecurityContext(
        long companyId,
        long userId,
        String username,
        String role,
        Long defaultBranchId,
        Set<Long> allowedBranchIds
) {
}
