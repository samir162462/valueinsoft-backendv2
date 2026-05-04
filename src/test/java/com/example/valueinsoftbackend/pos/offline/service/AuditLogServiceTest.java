package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.repository.SyncAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditLogService}.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private SyncAuditLogRepository auditRepo;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void logSyncEventSuccess() {
        auditLogService.logSyncEvent(1L, 2L, 100L, 500L, 10L, 5L, "EVENT", "Msg", "{}");

        verify(auditRepo).insertAuditLog(eq(1L), eq(2L), eq(100L), eq(500L), eq(10L), eq(5L), eq("EVENT"), eq("Msg"), eq("{}"));
    }

    @Test
    void logSyncEventSwallowsException() {
        doThrow(new RuntimeException("DB Error")).when(auditRepo)
                .insertAuditLog(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());

        // Should not throw exception
        assertDoesNotThrow(() -> 
                auditLogService.logSyncEvent(1L, 2L, 100L, 500L, 10L, 5L, "EVENT", "Msg", "{}")
        );
        
        verify(auditRepo).insertAuditLog(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

    private void assertDoesNotThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("Should not have thrown any exception");
        }
    }
}
