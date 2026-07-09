package com.example.valueinsoftbackend.security;

import com.example.valueinsoftbackend.AbstractIntegrationTest;
import com.example.valueinsoftbackend.SecurityPack.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-5 endpoint regression: repeated failed logins from the same client are throttled with
 * HTTP 429 after the configured threshold (default 5 attempts). Uses a non-existent user so
 * authentication fails as bad credentials (401) until the lockout engages.
 */
class LoginRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void resetLimiter() {
        // In-memory limiter is a singleton across the context; isolate this test.
        loginAttemptService.clearAll();
    }

    private static String body() {
        return """
                { "username": "attacker_unknown_user", "password": "wrong-password" }
                """;
    }

    @Test
    void blocksAfterThreshold_withTooManyRequests() throws Exception {
        // Default threshold is 5 failures -> attempts 1..5 return 401, attempt 6 is locked out.
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(MockMvcRequestBuilders.post("/authenticate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }

        mockMvc.perform(MockMvcRequestBuilders.post("/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }
}
