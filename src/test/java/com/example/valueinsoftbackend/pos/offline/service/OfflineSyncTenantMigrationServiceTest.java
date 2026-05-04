package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OfflineSyncTenantMigrationService}.
 */
@ExtendWith(MockitoExtension.class)
class OfflineSyncTenantMigrationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private OfflinePosProperties properties;

    @InjectMocks
    private OfflineSyncTenantMigrationService migrationService;

    @Test
    void runIfEnabledSkippedWhenDisabled() {
        when(properties.isRunTenantMigrationOnStartup()).thenReturn(false);

        migrationService.runIfEnabled();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void runIfEnabledExecutesMigrationForFoundSchemas() {
        when(properties.isRunTenantMigrationOnStartup()).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("c_1", "c_2"));

        migrationService.runIfEnabled();

        // 2 schemas * 3 functions = 6 calls to jdbcTemplate.query
        verify(jdbcTemplate, times(6)).query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
    }

    @Test
    void runIfEnabledContinuesOnException() {
        when(properties.isRunTenantMigrationOnStartup()).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("c_1", "c_2"));

        // Throw exception only on the first call, succeed on others
        doThrow(new RuntimeException("Schema 1 error"))
                .doNothing()
                .when(jdbcTemplate).query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));

        migrationService.runIfEnabled();

        // 1st schema: 1 failed call
        // 2nd schema: 3 successful calls
        // Total: 4 calls
        verify(jdbcTemplate, atLeast(4)).query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
    }
}
