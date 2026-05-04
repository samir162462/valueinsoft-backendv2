package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Service.PosSalePostingService;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineOrderImportStatus;
import com.example.valueinsoftbackend.pos.offline.enums.PosIdempotencyStatus;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.OfflineOrderImportModel;
import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OfflineOrderPostingProcessor} verifying claim, map, and post logic.
 */
@ExtendWith(MockitoExtension.class)
class OfflineOrderPostingProcessorTest {

    @Mock
    private OfflineOrderImportRepository importRepo;

    @Mock
    private PosIdempotencyService idempotencyService;

    @Mock
    private PosSalePostingService posSalePostingService;

    @Mock
    private SyncErrorService syncErrorService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OfflineOrderPostingProcessor postingProcessor;

    private static final Long COMPANY_ID = 1L;
    private static final Long BRANCH_ID = 2L;
    private static final Long BATCH_ID = 100L;
    private static final Long IMPORT_ID = 500L;
    private static final String IDEMPOTENCY_KEY = "idem-123";
    private static final String PAYLOAD_HASH = "hash-123";

    @BeforeEach
    void setUp() {
        // Mock TransactionTemplate to execute the logic immediately
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void postNextValidatedImportSuccess() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "totalAmount": 100,
                  "items": [
                    { "productId": 10, "quantity": 1, "unitPrice": 100, "lineTotal": 100 }
                  ],
                  "payments": [ { "amount": 100, "paymentMethod": "CASH" } ]
                }
                """;

        OfflineOrderImportModel importRecord = createImportModel(payload, null);
        when(importRepo.claimNextValidatedForPosting(COMPANY_ID, BRANCH_ID, BATCH_ID))
                .thenReturn(Optional.of(importRecord));
        
        when(idempotencyService.requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(createIdempotencyModel(null));

        DbPosOrder.AddOrderResult result = new DbPosOrder.AddOrderResult(999, 1, new java.sql.Timestamp(System.currentTimeMillis()));
        when(posSalePostingService.postSale(anyInt(), any())).thenReturn(result);

        boolean processed = postingProcessor.postNextValidatedImport(COMPANY_ID, BRANCH_ID, BATCH_ID);

        assertTrue(processed);
        verify(importRepo).markPostingSynced(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq(999L));
        verify(idempotencyService).markSynced(eq(COMPANY_ID), eq(BRANCH_ID), anyLong(), eq(IDEMPOTENCY_KEY), eq(999L), isNull());
    }

    @Test
    void postBlockedByDuplicate() {
        String payload = "{\"offlineOrderNo\":\"ORD-001\"}";
        OfflineOrderImportModel importRecord = createImportModel(payload, 888L);
        
        when(importRepo.claimImportForPosting(COMPANY_ID, BRANCH_ID, IMPORT_ID))
                .thenReturn(Optional.of(importRecord));

        when(idempotencyService.requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(createIdempotencyModel(888L));

        postingProcessor.postSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        verify(importRepo).markPostingFailed(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq("OFFLINE_DUPLICATE_POSTING_ATTEMPT"), anyString());
    }

    @Test
    void mapFailsOnDecimalAmount() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "totalAmount": 100.50,
                  "items": [
                    { "productId": 10, "quantity": 1, "unitPrice": 100.50, "lineTotal": 100.50 }
                  ]
                }
                """;
        OfflineOrderImportModel importRecord = createImportModel(payload, null);
        when(importRepo.claimImportForPosting(COMPANY_ID, BRANCH_ID, IMPORT_ID))
                .thenReturn(Optional.of(importRecord));

        when(idempotencyService.requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(createIdempotencyModel(null));

        postingProcessor.postSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        verify(importRepo).markPostingFailed(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq("OFFLINE_DECIMAL_AMOUNT_NOT_SUPPORTED"), anyString());
    }

    @Test
    void postFailsOnMultiTender() {
        String payload = """
                {
                  "offlineOrderNo": "ORD-001",
                  "idempotencyKey": "idem-123",
                  "totalAmount": 100,
                  "items": [ { "productId": 10, "quantity": 1, "unitPrice": 100, "lineTotal": 100 } ],
                  "payments": [
                    { "amount": 50, "paymentMethod": "CASH" },
                    { "amount": 50, "paymentMethod": "CARD" }
                  ]
                }
                """;
        OfflineOrderImportModel importRecord = createImportModel(payload, null);
        when(importRepo.claimImportForPosting(COMPANY_ID, BRANCH_ID, IMPORT_ID))
                .thenReturn(Optional.of(importRecord));

        when(idempotencyService.requireMatchingRecord(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(createIdempotencyModel(null));

        postingProcessor.postSingleImport(COMPANY_ID, BRANCH_ID, IMPORT_ID);

        verify(importRepo).markPostingFailed(eq(COMPANY_ID), eq(BRANCH_ID), eq(IMPORT_ID), eq("OFFLINE_MULTI_TENDER_NOT_SUPPORTED"), anyString());
    }

    private OfflineOrderImportModel createImportModel(String payloadJson, Long officialOrderId) {
        return new OfflineOrderImportModel(
                IMPORT_ID, BATCH_ID, COMPANY_ID, BRANCH_ID, 10L, 5L,
                "ORD-001", IDEMPOTENCY_KEY, Instant.now(), payloadJson, PAYLOAD_HASH,
                OfflineOrderImportStatus.VALIDATED, officialOrderId, null, null, null, 0,
                Instant.now(), null, null, Instant.now()
        );
    }

    private PosIdempotencyModel createIdempotencyModel(Long officialOrderId) {
        return new PosIdempotencyModel(
                1L, COMPANY_ID, BRANCH_ID, 10L, IDEMPOTENCY_KEY, "ORD-001", PAYLOAD_HASH,
                PosIdempotencyStatus.RECEIVED, officialOrderId, null, Instant.now(), Instant.now()
        );
    }
}
