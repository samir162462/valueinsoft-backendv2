package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.audit.AiRateLimitRepository;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimitServiceTest {

    private static final AiSecurityContext CONTEXT = new AiSecurityContext(
            42L, 7L, "sam", "OWNER", 100L, Set.of(100L)
    );

    @Test
    void atomicallyConsumesAnAvailableRequest() {
        AiProperties properties = new AiProperties();
        properties.setDailyUserRequestLimit(100);
        AiRateLimitRepository repository = mock(AiRateLimitRepository.class);
        when(repository.tryConsumeDailyUserRequest(42L, 7L, 100)).thenReturn(true);

        assertDoesNotThrow(() -> new AiRateLimitService(properties, repository)
                .validateDailyUserRequestLimit(CONTEXT));
        verify(repository).tryConsumeDailyUserRequest(42L, 7L, 100);
    }

    @Test
    void rejectsWhenAtomicQuotaConsumptionFails() {
        AiProperties properties = new AiProperties();
        properties.setDailyUserRequestLimit(100);
        AiRateLimitRepository repository = mock(AiRateLimitRepository.class);
        when(repository.tryConsumeDailyUserRequest(42L, 7L, 100)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> new AiRateLimitService(properties, repository).validateDailyUserRequestLimit(CONTEXT));

        assertEquals("AI_DAILY_USER_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void disabledLimitDoesNotTouchTheDatabase() {
        AiProperties properties = new AiProperties();
        properties.setDailyUserRequestLimit(0);
        AiRateLimitRepository repository = mock(AiRateLimitRepository.class);

        new AiRateLimitService(properties, repository).validateDailyUserRequestLimit(CONTEXT);

        verify(repository, never()).tryConsumeDailyUserRequest(42L, 7L, 0);
    }
}
