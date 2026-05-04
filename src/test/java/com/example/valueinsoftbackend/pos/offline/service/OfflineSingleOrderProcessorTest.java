package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OfflineSingleOrderProcessor}.
 */
@ExtendWith(MockitoExtension.class)
class OfflineSingleOrderProcessorTest {

    @Mock
    private OfflineOrderImportRepository importRepo;

    @Mock
    private PosIdempotencyService idempotencyService;

    @Mock
    private SyncErrorService syncErrorService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private OfflineSingleOrderProcessor processor;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long BATCH_ID = 100L;
    private static final Long IMPORT_ID = 500L;

    @Test
    void processNextPendingImportSuccess() {
        OfflineOrderImportModel importRecord = createImportModel();
        when(importRepo.claimNextPendingImport(COMPANY_ID, BRANCH_ID, BATCH_ID))
                .thenReturn(Optional.of(importRecord));

        boolean result = processor.processNextPendingImport(COMPANY_ID, BRANCH_ID, BATCH_ID);

        assertTrue(result);
        verify(idempotencyService).requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString());
        verify(importRepo).markReadyForValidation(COMPANY_ID, BRANCH_ID, IMPORT_ID);
        verify(auditLogService).logSyncEvent(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), eq("OFFLINE_ORDER_PROCESSING_COMPLETED_PLACEHOLDER"), anyString(), any());
    }

    @Test
    void processNextPendingImportIdempotencyFailure() {
        OfflineOrderImportModel importRecord = createImportModel();
        when(importRepo.claimNextPendingImport(COMPANY_ID, BRANCH_ID, BATCH_ID))
                .thenReturn(Optional.of(importRecord));
        
        doThrow(new OfflineSyncException("IDEMPOTENCY_PAYLOAD_MISMATCH", "Payload mismatch"))
                .when(idempotencyService).requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString());

        boolean result = processor.processNextPendingImport(COMPANY_ID, BRANCH_ID, BATCH_ID);

        assertTrue(result);
        verify(importRepo).markProcessingFailed(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq("IDEMPOTENCY_PAYLOAD_MISMATCH"), anyString());
        verify(syncErrorService).saveError(eq(IMPORT_ID), eq(COMPANY_ID), eq(BRANCH_ID), eq("PROCESSING_SKELETON"), eq("IDEMPOTENCY_PAYLOAD_MISMATCH"), anyString(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void processSingleImportNotFound() {
        when(importRepo.claimImportForProcessing(COMPANY_ID, BRANCH_ID, IMPORT_ID)).thenReturn(Optional.empty());

        boolean result = processor.processSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        assertFalse(result);
        verify(auditLogService).logSyncEvent(eq(COMPANY_ID), eq(BRANCH_ID), isNull(), eq(IMPORT_ID), isNull(), isNull(), eq("OFFLINE_ORDER_PROCESSING_SKIPPED"), anyString(), isNull());
    }

    @Test
    void processClaimedImportUnexpectedException() {
        OfflineOrderImportModel importRecord = createImportModel();
        when(importRepo.claimImportForProcessing(COMPANY_ID, BRANCH_ID, IMPORT_ID)).thenReturn(Optional.of(importRecord));
        
        doThrow(new RuntimeException("DB Error"))
                .when(idempotencyService).requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString());

        boolean result = processor.processSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        assertTrue(result);
        verify(importRepo).markProcessingFailed(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq("OFFLINE_PROCESSING_UNEXPECTED_ERROR"), contains("DB Error"));
    }

    private OfflineOrderImportModel createImportModel() {
        return new OfflineOrderImportModel(
                IMPORT_ID, BATCH_ID, COMPANY_ID, BRANCH_ID, 10L, 5L,
                "ORD-001", "idem-123", Instant.now(), "{}", "hash-123",
                OfflineOrderImportStatus.PENDING, null, null, null, null, 0,
                Instant.now(), null, null, Instant.now()
        );
    }
}
