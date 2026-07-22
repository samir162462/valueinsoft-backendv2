package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.provider.AiProvider;
import com.example.valueinsoftbackend.ai.provider.AiProviderException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRouterServiceTest {

    private final AiModelRequest request = new AiModelRequest("system", "user", "CHAT", "");

    @Test
    void selectsDefaultProvider() {
        AiProperties properties = properties("gemini", false);
        FakeProvider gemini = FakeProvider.success("gemini", "gemini-answer", "gemini-2.5-flash");
        FakeProvider deepseek = FakeProvider.success("deepseek", "deepseek-answer", "deepseek-chat");

        AiRouterService router = new AiRouterService(List.of(gemini, deepseek), properties, null);

        AiModelResponse response = router.generate(request);

        assertEquals("gemini-answer", response.answer());
        assertEquals("gemini-2.5-flash", response.modelName());
        assertFalse(response.fallback());
        assertEquals(1, gemini.calls);
        assertEquals(0, deepseek.calls);
    }

    @Test
    void selectsDeepSeekWhenConfigured() {
        AiProperties properties = properties("deepseek", false);
        FakeProvider gemini = FakeProvider.success("gemini", "gemini-answer", "gemini-2.5-flash");
        FakeProvider deepseek = FakeProvider.success("deepseek", "deepseek-answer", "deepseek-chat");

        AiRouterService router = new AiRouterService(List.of(gemini, deepseek), properties, null);

        AiModelResponse response = router.generate(request);

        assertEquals("deepseek-answer", response.answer());
        assertEquals("deepseek-chat", response.modelName());
        assertFalse(response.fallback());
        assertEquals(0, gemini.calls);
        assertEquals(1, deepseek.calls);
    }

    @Test
    void requestProviderOverridesConfiguredDefaultProvider() {
        AiProperties properties = properties("deepseek", false);
        FakeProvider gemini = FakeProvider.success("gemini", "gemini-answer", "gemini-2.5-flash");
        FakeProvider deepseek = FakeProvider.success("deepseek", "deepseek-answer", "deepseek-chat");
        AiRouterService router = new AiRouterService(List.of(gemini, deepseek), properties, null);

        AiModelResponse response = router.generate(new AiModelRequest(
                "system",
                "user",
                "CHAT",
                "",
                "",
                "gemini"
        ));

        assertEquals("gemini-answer", response.answer());
        assertEquals("gemini-2.5-flash", response.modelName());
        assertEquals("GEM", response.providerCode());
        assertEquals(1, gemini.calls);
        assertEquals(0, deepseek.calls);
    }

    @Test
    void fallbackTriggersForTimeoutServerAndRateLimitErrors() {
        List<AiProviderException.Category> categories = List.of(
                AiProviderException.Category.PROVIDER_TIMEOUT,
                AiProviderException.Category.PROVIDER_SERVER_ERROR,
                AiProviderException.Category.PROVIDER_RATE_LIMIT
        );

        for (AiProviderException.Category category : categories) {
            AiProperties properties = properties("gemini", true);
            properties.setFallbackProvider("deepseek");
            FakeProvider gemini = FakeProvider.failure("gemini", category);
            FakeProvider deepseek = FakeProvider.success("deepseek", "fallback-answer", "deepseek-chat");
            AiRouterService router = new AiRouterService(List.of(gemini, deepseek), properties, null);

            AiModelResponse response = router.generate(request);

            assertEquals("fallback-answer", response.answer());
            assertEquals("deepseek-chat", response.modelName());
            assertFalse(response.fallback());
            assertEquals(1, gemini.calls);
            assertEquals(1, deepseek.calls);
        }
    }

    @Test
    void validationAndUnsupportedProviderFailuresAreErrorsNotAnswers() {
        for (AiProviderException.Category category : List.of(
                AiProviderException.Category.VALIDATION_ERROR,
                AiProviderException.Category.UNSUPPORTED_PROVIDER)) {
            AiProperties properties = properties("gemini", true);
            properties.setFallbackProvider("deepseek");
            FakeProvider gemini = FakeProvider.failure("gemini", category);
            FakeProvider deepseek = FakeProvider.success("deepseek", "fallback-answer", "deepseek-chat");
            AiRouterService router = new AiRouterService(List.of(gemini, deepseek), properties, null);

            AiProviderException exception = assertThrows(AiProviderException.class, () -> router.generate(request));

            assertEquals(category, exception.getCategory());
            assertEquals(1, gemini.calls);
            assertEquals(0, deepseek.calls);
        }
    }

    @Test
    void providerOutageWithoutFallbackIsAnErrorNotACannedAnswer() {
        AiProperties properties = properties("gemini", false);
        FakeProvider gemini = FakeProvider.failure("gemini", AiProviderException.Category.PROVIDER_TIMEOUT);
        AiRouterService router = new AiRouterService(List.of(gemini), properties, null);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> router.generate(request));

        assertEquals(AiProviderException.Category.PROVIDER_TIMEOUT, exception.getCategory());
        assertEquals(1, gemini.calls);
    }

    @Test
    void providerMarkedFallbackResponseIsRejectedAsAnError() {
        AiProperties properties = properties("gemini", false);
        FakeProvider gemini = new FakeProvider(
                "gemini",
                new AiModelResponse("prepared fallback", "gemini-test", true, "gemini", "GEM"),
                null
        );
        AiRouterService router = new AiRouterService(List.of(gemini), properties, null);

        AiProviderException exception = assertThrows(AiProviderException.class, () -> router.generate(request));

        assertEquals(AiProviderException.Category.PROVIDER_BAD_RESPONSE, exception.getCategory());
    }

    @Test
    void existingGeminiPathStillWorksWithMockedProvider() {
        AiProperties properties = properties("google", false);
        FakeProvider gemini = FakeProvider.success("gemini", "gemini-answer", "gemini-2.5-flash");
        AiRouterService router = new AiRouterService(List.of(gemini), properties, null);

        AiModelResponse response = router.generate(request);

        assertEquals("gemini-answer", response.answer());
        assertEquals("gemini-2.5-flash", response.modelName());
        assertFalse(response.fallback());
        assertEquals(1, gemini.calls);
    }

    private AiProperties properties(String provider, boolean fallbackEnabled) {
        AiProperties properties = new AiProperties();
        properties.setProvider(provider);
        properties.setFallbackEnabled(fallbackEnabled);
        properties.getGemini().setModel("gemini-2.5-flash");
        properties.getDeepseek().setModel("deepseek-chat");
        return properties;
    }

    private static class FakeProvider implements AiProvider {
        private final String name;
        private final AiModelResponse response;
        private final AiProviderException.Category failureCategory;
        private int calls;

        private FakeProvider(String name, AiModelResponse response, AiProviderException.Category failureCategory) {
            this.name = name;
            this.response = response;
            this.failureCategory = failureCategory;
        }

        static FakeProvider success(String name, String answer, String model) {
            return new FakeProvider(name, new AiModelResponse(answer, model, false), null);
        }

        static FakeProvider failure(String name, AiProviderException.Category category) {
            return new FakeProvider(name, null, category);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AiModelResponse generate(AiModelRequest request) {
            calls++;
            if (failureCategory != null) {
                throw new AiProviderException(failureCategory, name, "safe failure");
            }
            return response;
        }
    }
}
