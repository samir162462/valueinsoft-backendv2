package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.enums.PosIdempotencyStatus;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;
import com.example.valueinsoftbackend.pos.offline.repository.PosIdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PosIdempotencyService}.
 */
@ExtendWith(MockitoExtension.class)
class PosIdempotencyServiceTest {

    @Mock
    private PosIdempotencyRepository idempotencyRepo;

    @InjectMocks
    private PosIdempotencyService idempotencyService;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long DEVICE_ID = 10L;
    private static final String KEY = "idem-123";
    private static final String HASH = "hash-123";

    @Test
    void claimIdempotencyKeySuccess() {
        PosIdempotencyModel model = createModel(HASH);
        when(idempotencyRepo.findByKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY)).thenReturn(Optional.of(model));

        IdempotencyClaimResult result = idempotencyService.claimIdempotencyKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY, "ORD-001", HASH);

        assertTrue(result.newlyClaimed());
        assertTrue(result.payloadMatches());
        verify(idempotencyRepo).insertReceivedKey(eq(COMPANY_ID), eq(BRANCH_ID), eq(DEVICE_ID), eq(KEY), anyString(), eq(HASH));
    }

    @Test
    void claimIdempotencyKeyDuplicateMatch() {
        PosIdempotencyModel model = createModel(HASH);
        doThrow(new DuplicateKeyException("Duplicate")).when(idempotencyRepo).insertReceivedKey(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        when(idempotencyRepo.findByKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY)).thenReturn(Optional.of(model));

        IdempotencyClaimResult result = idempotencyService.claimIdempotencyKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY, "ORD-001", HASH);

        assertFalse(result.newlyClaimed());
        assertTrue(result.payloadMatches());
    }

    @Test
    void claimIdempotencyKeyDuplicateMismatch() {
        PosIdempotencyModel model = createModel("different-hash");
        doThrow(new DuplicateKeyException("Duplicate")).when(idempotencyRepo).insertReceivedKey(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        when(idempotencyRepo.findByKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY)).thenReturn(Optional.of(model));

        IdempotencyClaimResult result = idempotencyService.claimIdempotencyKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY, "ORD-001", HASH);

        assertFalse(result.newlyClaimed());
        assertFalse(result.payloadMatches());
        verify(idempotencyRepo).markPayloadMismatch(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY);
    }

    @Test
    void requireMatchingRecordSuccess() {
        PosIdempotencyModel model = createModel(HASH);
        when(idempotencyRepo.findByKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY)).thenReturn(Optional.of(model));

        PosIdempotencyModel result = idempotencyService.requireMatchingRecord(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY, HASH);

        assertNotNull(result);
        assertEquals(HASH, result.requestHash());
    }

    @Test
    void requireMatchingRecordMismatch() {
        PosIdempotencyModel model = createModel("old-hash");
        when(idempotencyRepo.findByKey(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY)).thenReturn(Optional.of(model));

        assertThrows(OfflineSyncException.class, () -> 
                idempotencyService.requireMatchingRecord(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY, "new-hash")
        );
        verify(idempotencyRepo).markPayloadMismatch(COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY);
    }

    private PosIdempotencyModel createModel(String hash) {
        return new PosIdempotencyModel(
                1L, COMPANY_ID, BRANCH_ID, DEVICE_ID, KEY, "ORD-001", hash,
                PosIdempotencyStatus.RECEIVED, null, null, Instant.now(), Instant.now()
        );
    }
}
