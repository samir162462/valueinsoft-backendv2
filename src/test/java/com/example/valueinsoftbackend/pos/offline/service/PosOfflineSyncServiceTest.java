package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineSyncUploadRequest;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineSyncUploadResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncStatusResponse;
import com.example.valueinsoftbackend.pos.offline.enums.PosClientType;
import com.example.valueinsoftbackend.pos.offline.enums.PosSyncBatchStatus;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import com.example.valueinsoftbackend.pos.offline.repository.PosSyncBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PosOfflineSyncService}.
 */
@ExtendWith(MockitoExtension.class)
class PosOfflineSyncServiceTest {

    @Mock private PosSyncBatchRepository batchRepo;
    @Mock private OfflineOrderImportRepository importRepo;
    @Mock private PosDeviceService deviceService;
    @Mock private OfflineOrderValidationService validationService;
    @Mock private PosIdempotencyService idempotencyService;
    @Mock private SyncErrorService syncErrorService;
    @Mock private AuditLogService auditLogService;
    @Mock private OfflineSingleOrderProcessor singleOrderProcessor;
    @Mock private OfflineOrderValidationProcessor validationProcessor;
    @Mock private OfflineOrderPostingProcessor postingProcessor;
    @Mock private OfflinePosProperties props;

    @InjectMocks
    private PosOfflineSyncService syncService;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long DEVICE_ID = 10L;

    @BeforeEach
    void setUp() {
        lenient().when(props.isAllowOfflineSync()).thenReturn(true);
        lenient().when(props.getMaxOrdersPerBatch()).thenReturn(100);
    }

    @Test
    void uploadOfflineSyncSuccess() {
        OfflineSyncUploadRequest request = new OfflineSyncUploadRequest(
                COMPANY_ID, BRANCH_ID, DEVICE_ID, 50L,
                PosClientType.REACT_NATIVE_ANDROID, "Pixel", "1.0", "B-123",
                Instant.now(), Instant.now(), Collections.emptyList()
        );

        when(batchRepo.insertBatch(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt(), any(), any()))
                .thenReturn(1000L);

        OfflineSyncUploadResponse response = syncService.uploadOfflineSync(request, "user1");

        assertNotNull(response);
        assertEquals(1000L, response.syncBatchId());
        assertEquals("B-123", response.clientBatchId());
        verify(deviceService).validateDeviceForOfflineSync(COMPANY_ID, BRANCH_ID, DEVICE_ID);
        verify(validationService).validateBatch(request);
    }

    @Test
    void uploadOfflineSyncDisabled() {
        when(props.isAllowOfflineSync()).thenReturn(false);
        OfflineSyncUploadRequest request = mock(OfflineSyncUploadRequest.class);

        assertThrows(OfflineSyncException.class, () -> syncService.uploadOfflineSync(request, "user1"));
    }

    @Test
    void getSyncStatusSuccess() {
        PosSyncBatchModel batch = new PosSyncBatchModel(
                1000L, COMPANY_ID, BRANCH_ID, DEVICE_ID, 50L, "B-123", "ANDROID", "Pixel", "1.0",
                PosSyncBatchStatus.RECEIVED, 10, 0, 0, 0, 0, Instant.now(), Instant.now(), null, Instant.now(), Instant.now()
        );
        when(batchRepo.findById(COMPANY_ID, BRANCH_ID, 1000L)).thenReturn(Optional.of(batch));

        SyncStatusResponse response = syncService.getSyncStatus(COMPANY_ID, BRANCH_ID, 1000L, "user1");

        assertEquals(1000L, response.syncBatchId());
        assertEquals(PosSyncBatchStatus.RECEIVED, response.status());
    }

    @Test
    void getSyncStatusNotFound() {
        when(batchRepo.findById(anyLong(), anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThrows(OfflineSyncException.class, () -> syncService.getSyncStatus(COMPANY_ID, BRANCH_ID, 999L, "user1"));
    }
}
