package com.example.valueinsoftbackend.ai.dto;

public record AiStreamChunk(
        String type,    // "thinking", "tool_call", "delta", "sources", "actions", "suggestions", "done", "error"
        String content,
        Object data
) {
}
