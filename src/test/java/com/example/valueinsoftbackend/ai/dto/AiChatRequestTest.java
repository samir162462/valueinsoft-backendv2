package com.example.valueinsoftbackend.ai.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatRequestTest {

    @Test
    void realModelModeIsEnforcedForEveryClientVersion() {
        assertTrue(new AiChatRequest(null, "HELP", "How do I add a product?", null, null, null).useRealAiOnly());
        assertTrue(new AiChatRequest(null, "HELP", "How do I add a product?", null, false, null).useRealAiOnly());
        assertTrue(new AiChatRequest(null, "HELP", "How do I add a product?", null, true, null).useRealAiOnly());
    }
}
