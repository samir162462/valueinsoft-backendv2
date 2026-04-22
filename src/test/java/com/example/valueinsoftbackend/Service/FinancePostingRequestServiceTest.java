package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinancePostingRequest;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestProcessResponse;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancePostingRequestServiceTest {

    private static final int COMPANY_ID = 7;
    private static final int BRANCH_ID = 3;
    private static final UUID FISCAL_PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000005001");
    private static final UUID POSTING_REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000005002");
    private static final UUID JOURNAL_ID = UUID.fromString("00000000-0000-0000-0000-000000005003");

    private DbFinancePostingRequest dbFinancePostingRequest;
    private DbFinanceSetup dbFinanceSetup;
    private DbFinanceJournal dbFinanceJournal;
    private AuthorizationService authorizationService;
    private FinanceAuditService financeAuditService;
    private FinancePostingAdapter postingAdapter;
    private FinancePostingRequestService service;

    @BeforeEach
    void setUp() {
        dbFinancePostingRequest = Mockito.mock(DbFinancePostingRequest.class);
        dbFinanceSetup = Mockito.mock(DbFinanceSetup.class);
        dbFinanceJournal = Mockito.mock(DbFinanceJournal.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        financeAuditService = Mockito.mock(FinanceAuditService.class);
        postingAdapter = Mockito.mock(FinancePostingAdapter.class);

        service = new FinancePostingRequestService(
                dbFinancePostingRequest,
                dbFinanceSetup,
                dbFinanceJournal,
                authorizationService,
                financeAuditService,
                new ObjectMapper(),
                List.of(postingAdapter));

        when(dbFinanceSetup.companyExists(COMPANY_ID)).thenReturn(true);
        when(dbFinanceSetup.branchBelongsToCompany(COMPANY_ID, BRANCH_ID)).thenReturn(true);
        when(dbFinanceSetup.fiscalPeriodExists(COMPANY_ID, FISCAL_PERIOD_ID)).thenReturn(true);
        when(dbFinanceJournal.getFiscalPeriodPostingInfo(COMPANY_ID, FISCAL_PERIOD_ID))
                .thenReturn(new DbFinanceJournal.FiscalPeriodPostingInfo(
                        FISCAL_PERIOD_ID,
                        UUID.fromString("00000000-0000-0000-0000-000000005101"),
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        "open"));
        when(financeAuditService.resolveActorUserId("sam")).thenReturn(17);
        when(financeAuditService.recordEvent(any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn("corr-5");
        when(postingAdapter.supports("pos")).thenReturn(true);
    }

    @Test
    void createPostingRequestNormalizesInputCalculatesHashAndPersistsPendingRequest() {
        FinancePostingRequestCreateRequest createRequest = createRequest();
        when(dbFinancePostingRequest.findPostingRequestBySource(COMPANY_ID, "pos", "sale", "sale-5001"))
                .thenReturn(null);
        when(dbFinancePostingRequest.createPostingRequest(any(), any(), any(), eq(17)))
                .thenAnswer(invocation -> postingRequest(
                        "pending",
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        null,
                        null,
                        0));

        FinancePostingRequestItem created = service.createPostingRequestForAuthenticatedUser("sam", createRequest);

        ArgumentCaptor<FinancePostingRequestCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(FinancePostingRequestCreateRequest.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(dbFinancePostingRequest).createPostingRequest(
                requestCaptor.capture(),
                hashCaptor.capture(),
                payloadCaptor.capture(),
                eq(17));

        assertEquals("pos", requestCaptor.getValue().getSourceModule());
        assertEquals("sale", requestCaptor.getValue().getSourceType());
        assertEquals("sale-5001", requestCaptor.getValue().getSourceId());
        assertNotNull(hashCaptor.getValue());
        assertEquals(64, hashCaptor.getValue().length());
        assertEquals("{\"amount\":115,\"currencyCode\":\"EGP\"}", payloadCaptor.getValue());
        assertEquals("pending", created.getStatus());
        assertEquals(hashCaptor.getValue(), created.getRequestHash());
    }

    @Test
    void createPostingRequestReturnsExistingWhenDuplicateHasSameHash() {
        AtomicReference<String> capturedHash = new AtomicReference<>();
        AtomicReference<String> capturedPayload = new AtomicReference<>();
        when(dbFinancePostingRequest.findPostingRequestBySource(COMPANY_ID, "pos", "sale", "sale-5001"))
                .thenReturn(null);
        when(dbFinancePostingRequest.findPostingRequestBySource(COMPANY_ID, "pos", "sale", "sale-5001"))
                .thenReturn(null)
                .thenAnswer(invocation -> postingRequest("pending",
                        capturedHash.get(),
                        capturedPayload.get(),
                        null,
                        null,
                        0));
        when(dbFinancePostingRequest.createPostingRequest(any(), any(), any(), eq(17)))
                .thenAnswer(invocation -> {
                    capturedHash.set(invocation.getArgument(1));
                    capturedPayload.set(invocation.getArgument(2));
                    throw new DuplicateKeyException("duplicate");
                });

        FinancePostingRequestItem existing = service.createPostingRequestForAuthenticatedUser("sam", createRequest());

        assertEquals("pending", existing.getStatus());
        assertEquals(capturedHash.get(), existing.getRequestHash());
    }

    @Test
    void createPostingRequestRejectsExistingSourceWithDifferentHash() {
        when(dbFinancePostingRequest.findPostingRequestBySource(COMPANY_ID, "pos", "sale", "sale-5001"))
                .thenReturn(postingRequest("pending", "different-hash", "{\"amount\":999}", null, null, 0));

        ApiException exception = assertThrows(ApiException.class, () ->
                service.createPostingRequestForAuthenticatedUser("sam", createRequest()));

        assertEquals("FINANCE_POSTING_REQUEST_HASH_CONFLICT", exception.getCode());
        verify(dbFinancePostingRequest, never()).createPostingRequest(any(), any(), any(), any());
    }

    @Test
    void createPostingRequestFromSystemUsesSameValidationWithoutUserAuthorization() {
        FinancePostingRequestCreateRequest createRequest = createRequest();
        when(dbFinancePostingRequest.findPostingRequestBySource(COMPANY_ID, "pos", "sale", "sale-5001"))
                .thenReturn(null);
        when(dbFinancePostingRequest.createPostingRequest(any(), any(), any(), eq(17)))
                .thenAnswer(invocation -> postingRequest(
                        "pending",
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        null,
                        null,
                        0));

        FinancePostingRequestItem created = service.createPostingRequestFromSystem("sam", createRequest);

        assertEquals("pending", created.getStatus());
        verify(authorizationService, never()).assertAuthenticatedCapability(any(), anyInt(), any(), any());
        verify(dbFinancePostingRequest).createPostingRequest(any(), any(), any(), eq(17));
    }

    @Test
    void processPostingRequestDispatchesAdapterAndMarksPosted() {
        FinancePostingRequestItem claimed = postingRequest("processing", "hash-1", "{}", null, null, 1);
        FinancePostingRequestItem posted = postingRequest("posted", "hash-1", "{}", null, JOURNAL_ID, 1);
        when(dbFinancePostingRequest.claimPendingPostingRequest(COMPANY_ID, POSTING_REQUEST_ID, 17))
                .thenReturn(claimed);
        when(postingAdapter.post(claimed)).thenReturn(JOURNAL_ID);
        when(dbFinancePostingRequest.markPostingRequestPosted(COMPANY_ID, POSTING_REQUEST_ID, JOURNAL_ID, 17))
                .thenReturn(posted);

        FinancePostingRequestProcessResponse response = service.processPostingRequestForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                POSTING_REQUEST_ID);

        assertEquals(true, response.isProcessed());
        assertEquals("posted", response.getStatus());
        assertEquals(JOURNAL_ID, response.getJournalEntryId());
        assertEquals("Posting request processed", response.getMessage());
        assertEquals("corr-5", response.getCorrelationId());
    }

    @Test
    void processPostingRequestMarksFailedWhenAdapterThrows() {
        FinancePostingRequestItem claimed = postingRequest("processing", "hash-1", "{}", null, null, 2);
        FinancePostingRequestItem failed = postingRequest("failed", "hash-1", "{}", "adapter failed", null, 2);
        when(dbFinancePostingRequest.claimPendingPostingRequest(COMPANY_ID, POSTING_REQUEST_ID, 17))
                .thenReturn(claimed);
        when(postingAdapter.post(claimed)).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "FINANCE_TEST_FAILURE",
                "adapter failed"));
        when(dbFinancePostingRequest.markPostingRequestFailed(COMPANY_ID, POSTING_REQUEST_ID, "adapter failed", 17))
                .thenReturn(failed);

        FinancePostingRequestProcessResponse response = service.processPostingRequestForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                POSTING_REQUEST_ID);

        assertEquals(true, response.isProcessed());
        assertEquals("failed", response.getStatus());
        assertEquals(null, response.getJournalEntryId());
        assertEquals("adapter failed", response.getMessage());
        assertEquals("corr-5", response.getCorrelationId());
    }

    @Test
    void processNextReturnsNoopWhenNoPendingRequestExists() {
        when(dbFinancePostingRequest.claimNextPendingPostingRequest(COMPANY_ID, "pos", 17))
                .thenReturn(null);

        FinancePostingRequestProcessResponse response = service.processNextPostingRequestForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                "pos");

        assertEquals(false, response.isProcessed());
        assertEquals("none", response.getStatus());
        assertEquals("No pending posting request was available", response.getMessage());
    }

    @Test
    void retryPostingRequestMovesFailedRequestBackToPending() {
        FinancePostingRequestItem failed = postingRequest("failed", "hash-1", "{}", "adapter failed", null, 2);
        FinancePostingRequestItem retried = postingRequest("pending", "hash-1", "{}", null, null, 2);
        when(dbFinancePostingRequest.getPostingRequestById(COMPANY_ID, POSTING_REQUEST_ID)).thenReturn(failed, retried);
        when(dbFinancePostingRequest.retryFailedPostingRequest(COMPANY_ID, POSTING_REQUEST_ID, 17)).thenReturn(1);

        FinancePostingRequestItem response = service.retryPostingRequestForAuthenticatedUser(
                "sam",
                COMPANY_ID,
                POSTING_REQUEST_ID);

        assertEquals("pending", response.getStatus());
        assertEquals(null, response.getLastError());
    }

    @Test
    void retryPostingRequestRejectsNonFailedRequest() {
        FinancePostingRequestItem posted = postingRequest("posted", "hash-1", "{}", null, JOURNAL_ID, 1);
        when(dbFinancePostingRequest.getPostingRequestById(COMPANY_ID, POSTING_REQUEST_ID)).thenReturn(posted);
        when(dbFinancePostingRequest.retryFailedPostingRequest(COMPANY_ID, POSTING_REQUEST_ID, 17)).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.retryPostingRequestForAuthenticatedUser("sam", COMPANY_ID, POSTING_REQUEST_ID));

        assertEquals("FINANCE_POSTING_REQUEST_NOT_RETRYABLE", exception.getCode());
    }

    private FinancePostingRequestCreateRequest createRequest() {
        return new FinancePostingRequestCreateRequest(
                COMPANY_ID,
                BRANCH_ID,
                " POS ",
                " Sale ",
                " SALE-5001 ",
                LocalDate.of(2026, 6, 12),
                FISCAL_PERIOD_ID,
                Map.of(
                        "currencyCode", "EGP",
                        "amount", 115));
    }

    private FinancePostingRequestItem postingRequest(String status,
                                                     String requestHash,
                                                     String payloadJson,
                                                     String lastError,
                                                     UUID journalEntryId,
                                                     int attemptCount) {
        return new FinancePostingRequestItem(
                POSTING_REQUEST_ID,
                COMPANY_ID,
                BRANCH_ID,
                null,
                "pos",
                "sale",
                "sale-5001",
                LocalDate.of(2026, 6, 12),
                FISCAL_PERIOD_ID,
                requestHash,
                payloadJson,
                status,
                attemptCount,
                Instant.now(),
                lastError,
                journalEntryId,
                Instant.now(),
                15,
                Instant.now(),
                17);
    }
}
