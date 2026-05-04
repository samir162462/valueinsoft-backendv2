package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OfflineOrderValidationProcessor}.
 */
@ExtendWith(MockitoExtension.class)
class OfflineOrderValidationProcessorTest {

    @Mock
    private OfflineOrderImportRepository importRepo;

    @Mock
    private OfflineOrderImportValidationService validationService;

    @Mock
    private SyncErrorService syncErrorService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private OfflineOrderValidationProcessor validationProcessor;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long BATCH_ID = 100L;
    private static final Long IMPORT_ID = 500L;

    @Test
    void validateNextReadyImportSuccess() {
        OfflineOrderImportModel importRecord = createImportModel();
        when(importRepo.claimNextReadyForValidation(COMPANY_ID, BRANCH_ID, BATCH_ID))
                .thenReturn(Optional.of(importRecord));
        
        when(validationService.validate(importRecord)).thenReturn(List.of());

        boolean result = validationProcessor.validateNextReadyImport(COMPANY_ID, BRANCH_ID, BATCH_ID);

        assertTrue(result);
        verify(importRepo).markValidated(COMPANY_ID, BRANCH_ID, IMPORT_ID);
        verify(auditLogService).logSyncEvent(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), eq("OFFLINE_ORDER_VALIDATION_PASSED"), anyString(), any());
    }

    @Test
    void validateNextReadyImportFailure() {
        OfflineOrderImportModel importRecord = createImportModel();
        when(importRepo.claimNextReadyForValidation(COMPANY_ID, BRANCH_ID, BATCH_ID))
                .thenReturn(Optional.of(importRecord));
        
        OfflineOrderImportValidationService.ValidationError error = 
                new OfflineOrderImportValidationService.ValidationError("TEST_ERROR", "Test message", "items[0]");
        when(validationService.validate(importRecord)).thenReturn(List.of(error));

        boolean result = validationProcessor.validateNextReadyImport(COMPANY_ID, BRANCH_ID, BATCH_ID);

        assertTrue(result);
        verify(importRepo).markValidationFailed(COMPANY_ID, BRANCH_ID, IMPORT_ID, "TEST_ERROR", "Test message");
        verify(syncErrorService).saveError(eq(IMPORT_ID), eq(COMPANY_ID), eq(BRANCH_ID), eq("VALIDATION"), eq("TEST_ERROR"), anyString(), anyString(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void validateSingleImportNotFound() {
        when(importRepo.claimImportForValidation(COMPANY_ID, BRANCH_ID, IMPORT_ID)).thenReturn(Optional.empty());

        boolean result = validationProcessor.validateSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        assertFalse(result);
        verify(auditLogService).logSyncEvent(eq(COMPANY_ID), eq(BRANCH_ID), isNull(), eq(IMPORT_ID), isNull(), isNull(), eq("OFFLINE_ORDER_VALIDATION_SKIPPED"), anyString(), isNull());
    }

    @Test
    void validateClaimedImportUnexpectedException() {
        OfflineOrderImportModel importRecord = createImportModel();
        when(importRepo.claimImportForValidation(COMPANY_ID, BRANCH_ID, IMPORT_ID)).thenReturn(Optional.of(importRecord));
        
        when(validationService.validate(importRecord)).thenThrow(new RuntimeException("Database down"));

        boolean result = validationProcessor.validateSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        assertTrue(result);
        verify(importRepo).markValidationFailed(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq("OFFLINE_ORDER_VALIDATION_UNEXPECTED_ERROR"), contains("Database down"));
        verify(syncErrorService).saveError(eq(IMPORT_ID), eq(COMPANY_ID), eq(BRANCH_ID), eq("VALIDATION"), eq("OFFLINE_ORDER_VALIDATION_UNEXPECTED_ERROR"), anyString(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    private OfflineOrderImportModel createImportModel() {
        return new OfflineOrderImportModel(
                IMPORT_ID, BATCH_ID, COMPANY_ID, BRANCH_ID, 10L, 5L,
                "ORD-001", "idem-123", Instant.now(), "{}", "hash-123",
                OfflineOrderImportStatus.READY_FOR_VALIDATION, null, null, null, null, 0,
                Instant.now(), null, null, Instant.now()
        );
    }
}
