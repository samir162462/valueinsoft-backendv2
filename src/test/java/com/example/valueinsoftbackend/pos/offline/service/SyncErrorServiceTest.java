package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorItemResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorListResponse;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderErrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SyncErrorService}.
 */
@ExtendWith(MockitoExtension.class)
class SyncErrorServiceTest {

    @Mock
    private OfflineOrderErrorRepository errorRepo;

    @InjectMocks
    private SyncErrorService errorService;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long BATCH_ID = 100L;

    @Test
    void saveErrorSuccess() {
        errorService.saveError(500L, COMPANY_ID, BRANCH_ID, "VALIDATION", "ERR_01", "Msg", null, null, OfflineErrorSeverity.NEEDS_REVIEW, true, false);

        verify(errorRepo).insertError(eq(500L), eq(COMPANY_ID), eq(BRANCH_ID), eq("VALIDATION"), eq("ERR_01"), eq("Msg"), isNull(), isNull(), eq(OfflineErrorSeverity.NEEDS_REVIEW), eq(true), eq(false));
    }

    @Test
    void getErrorsByBatchIdPaginatedHasMore() {
        // We ask for 2, repository returns 3 to indicate hasMore
        SyncErrorItemResponse error1 = createErrorItem(1L);
        SyncErrorItemResponse error2 = createErrorItem(2L);
        SyncErrorItemResponse error3 = createErrorItem(3L);
        
        when(errorRepo.findErrorsByBatchId(eq(COMPANY_ID), eq(BRANCH_ID), eq(BATCH_ID), eq(0L), eq(3)))
                .thenReturn(List.of(error1, error2, error3));

        SyncErrorListResponse response = errorService.getErrorsByBatchId(COMPANY_ID, BRANCH_ID, BATCH_ID, 0L, 2);

        assertEquals(2, response.errors().size());
        assertTrue(response.hasMore());
        assertEquals("2", response.nextCursor());
    }

    @Test
    void getErrorsByBatchIdPaginatedNoMore() {
        SyncErrorItemResponse error1 = createErrorItem(1L);
        
        when(errorRepo.findErrorsByBatchId(eq(COMPANY_ID), eq(BRANCH_ID), eq(BATCH_ID), eq(0L), eq(3)))
                .thenReturn(List.of(error1));

        SyncErrorListResponse response = errorService.getErrorsByBatchId(COMPANY_ID, BRANCH_ID, BATCH_ID, 0L, 2);

        assertEquals(1, response.errors().size());
        assertFalse(response.hasMore());
        assertNull(response.nextCursor());
    }

    private SyncErrorItemResponse createErrorItem(Long id) {
        return new SyncErrorItemResponse(
                id, 500L, "VALIDATION", "CODE", "Msg", "path", "val",
                OfflineErrorSeverity.NEEDS_REVIEW, false, true, java.time.Instant.now()
        );
    }
}
