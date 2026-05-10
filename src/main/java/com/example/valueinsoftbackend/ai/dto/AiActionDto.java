package com.example.valueinsoftbackend.ai.dto;

import java.util.Map;

public record AiActionDto(
        String label,
        String type,
        String route,
        Map<String, Object> params
) {
}
