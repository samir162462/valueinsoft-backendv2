package com.example.valueinsoftbackend.ai.dto;

public record AiToolCallDto(
        String toolName,
        String status,
        String summary
) {
}
