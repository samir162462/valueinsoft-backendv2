package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import com.example.valueinsoftbackend.pos.offline.repository.PosSyncBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosOfflineSyncServiceOperationalTest {

    private PosSyncBatchRepository batchRepo;
    private OfflineOrderImportRepository importRepo;
    private AuditLogService auditLogService;
    private PosOfflineSyncService service;

    @BeforeEach
    void setUp() {
        batchRepo = mock(PosSyncBatchRepository.class);
        importRepo = mock(OfflineOrderImportRepository.class);
        auditLogService = mock(AuditLogService.class);

        service = new PosOfflineSyncService(
                batchRepo,
                importRepo,
                mock(PosDeviceService.class),
                mock(OfflineOrderValidationService.class),
                mock(PosIdempotencyService.class),
                mock(SyncErrorService.class),
                auditLogService,
                mock(OfflineSingleOrderProcessor.class),
                mock(OfflineOrderValidationProcessor.class),
                mock(OfflineOrderPostingProcessor.class),
                new OfflinePosProperties());
    }

    @Test
    void recoverStuckImportsMovesPostingToNeedsReviewAndRecalculatesBatch() {
        when(batchRepo.findById(1095L, 1L, 20L)).thenReturn(Optional.of(batch()));
        when(importRepo.markStuckProcessingFailed(1095L, 1L, 20L, 15)).thenReturn(1);
        when(importRepo.markStuckValidatingFailed(1095L, 1L, 20L, 15)).thenReturn(2);
        when(importRepo.markStuckPostingNeedsReview(1095L, 1L, 20L, 15)).thenReturn(3);

        int recovered = service.recoverStuckImports(1095L, 1L, 20L, 15);

        assertEquals(6, recovered);
        verify(importRepo).markStuckPostingNeedsReview(1095L, 1L, 20L, 15);
        verify(batchRepo).recalculateSummary(1095L, 1L, 20L);
        verify(auditLogService).logSyncEvent(
                1095L,
                1L,
                20L,
                null,
                null,
                null,
                "OFFLINE_STUCK_IMPORT_RECOVERY",
                "Recovered stuck imports: processing=1, validating=2, postingNeedsReview=3",
                null);
    }

    private PosSyncBatchModel batch() {
        Instant now = Instant.now();
        return new PosSyncBatchModel(
                20L,
                1095L,
                1L,
                5L,
                9L,
                "batch-1",
                "WEB",
                "windows",
                "1.0",
                PosSyncBatchStatus.IN_PROGRESS,
                6,
                0,
                0,
                0,
                0,
                now,
                now,
                null,
                now,
                now);
    }
}
