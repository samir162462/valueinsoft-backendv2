package com.example.valueinsoftbackend.ai.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatRequestTest {

    @Test
    void useRealAiOnlyReflectsRequestFlag() {
        assertFalse(new AiChatRequest(null, "HELP", "How do I add a product?", null, null, null).useRealAiOnly());
        assertFalse(new AiChatRequest(null, "HELP", "How do I add a product?", null, false, null).useRealAiOnly());
        assertTrue(new AiChatRequest(null, "HELP", "How do I add a product?", null, true, null).useRealAiOnly());
    }
}
