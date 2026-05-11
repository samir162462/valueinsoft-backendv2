package com.example.valueinsoftbackend.ai.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AiAdminToolAuditResponse(
        Instant generatedAt,
        LocalDate fromDate,
        LocalDate toDate,
        List<AiAdminToolAuditItemDto> items
) {
}
