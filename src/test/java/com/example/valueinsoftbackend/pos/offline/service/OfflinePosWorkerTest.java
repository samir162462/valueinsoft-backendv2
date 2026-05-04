package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosWorkerProperties;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.pos.offline.repository.PosSyncBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OfflinePosWorker}.
 */
@ExtendWith(MockitoExtension.class)
class OfflinePosWorkerTest {

    @Mock
    private OfflinePosWorkerProperties properties;

    @Mock
    private PosSyncBatchRepository batchRepo;

    @Mock
    private PosOfflineSyncService syncService;

    @InjectMocks
    private OfflinePosWorker worker;

    @Test
    void runCycleSkippedWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        worker.runCycle();

        verifyNoInteractions(batchRepo);
        verifyNoInteractions(syncService);
    }

    @Test
    void runCycleProcessesMultipleTargetsAndStages() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getTargets()).thenReturn("1:10, 2:20");
        when(properties.getBatchSize()).thenReturn(5);
        when(properties.getStuckThresholdMinutes()).thenReturn(30);
        
        // Target 1 stages: Processing + Validation
        when(properties.isProcessingEnabled()).thenReturn(true);
        when(properties.isValidationEnabled()).thenReturn(true);
        when(properties.isPostingEnabled()).thenReturn(false);

        PosSyncBatchModel batch1 = createBatch(101L, 1L, 10L);
        PosSyncBatchModel batch2 = createBatch(201L, 2L, 20L);

        when(batchRepo.findActiveBatchesForWorker(eq(1L), eq(10L), anyInt())).thenReturn(List.of(batch1));
        when(batchRepo.findActiveBatchesForWorker(eq(2L), eq(20L), anyInt())).thenReturn(List.of(batch2));

        worker.runCycle();

        // Verify Batch 1
        verify(syncService).recoverStuckImports(eq(1L), eq(10L), eq(101L), eq(30));
        verify(syncService).processPendingImports(eq(1L), eq(10L), eq(101L));
        verify(syncService).validateReadyImports(eq(1L), eq(10L), eq(101L));
        verify(syncService, never()).postValidatedImports(eq(1L), eq(10L), eq(101L));
        verify(syncService).recalculateBatchSummary(eq(1L), eq(10L), eq(101L));

        // Verify Batch 2
        verify(syncService).recoverStuckImports(eq(2L), eq(20L), eq(201L), eq(30));
        verify(syncService).processPendingImports(eq(2L), eq(20L), eq(201L));
        verify(syncService).validateReadyImports(eq(2L), eq(20L), eq(201L));
        verify(syncService, never()).postValidatedImports(eq(2L), eq(20L), eq(201L));
        verify(syncService).recalculateBatchSummary(eq(2L), eq(20L), eq(201L));
    }

    @Test
    void runCycleHandlesInvalidTargetFormatGracefully() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getTargets()).thenReturn("invalid_format, 1:10");
        
        PosSyncBatchModel batch = createBatch(101L, 1L, 10L);
        when(batchRepo.findActiveBatchesForWorker(eq(1L), eq(10L), anyInt())).thenReturn(List.of(batch));

        worker.runCycle();

        verify(batchRepo, times(1)).findActiveBatchesForWorker(anyLong(), anyLong(), anyInt());
        verify(syncService, times(1)).recalculateBatchSummary(anyLong(), anyLong(), anyLong());
    }

    private PosSyncBatchModel createBatch(Long id, Long companyId, Long branchId) {
        return new PosSyncBatchModel(
                id, companyId, branchId, 10L, 5L, "B-001", "DESKTOP", "WINDOWS", "1.0",
                PosSyncBatchStatus.PROCESSING, 0, 0, 0, 0, 0,
                Instant.now(), null, null, Instant.now(), Instant.now()
        );
    }
}
