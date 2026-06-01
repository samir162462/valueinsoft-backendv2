package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.PlatformAuthorizationService;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentCreateRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentListResponse;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeIngestResponse;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeSearchTestRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeSearchTestResponse;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingException;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.ai.service.AiSecurityContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeAdminServiceTest {

    private final AiSecurityContextResolver securityContextResolver = mock(AiSecurityContextResolver.class);
    private final AiPermissionService permissionService = mock(AiPermissionService.class);
    private final PlatformAuthorizationService platformAuthorizationService = mock(PlatformAuthorizationService.class);
    private final AiKnowledgeDocumentService documentService = mock(AiKnowledgeDocumentService.class);
    private final AiKnowledgeDocumentRepository documentRepository = mock(AiKnowledgeDocumentRepository.class);
    private final AiKnowledgeChunkRepository chunkRepository = mock(AiKnowledgeChunkRepository.class);
    private final AiKnowledgeIngestionService ingestionService = mock(AiKnowledgeIngestionService.class);
    private final AiKnowledgeIngestionJobRepository ingestionJobRepository = mock(AiKnowledgeIngestionJobRepository.class);
    private final AiRetrieverService retrieverService = mock(AiRetrieverService.class);
    private final DbBranch dbBranch = mock(DbBranch.class);

    private final Principal principal = () -> "admin";
    private final AiSecurityContext context = new AiSecurityContext(
            10L,
            77L,
            "admin",
            "ADMIN",
            100L,
            Set.of(100L, 101L)
    );

    private AiKnowledgeAdminService service;

    @BeforeEach
    void setUp() {
        service = new AiKnowledgeAdminService(
                securityContextResolver,
                permissionService,
                platformAuthorizationService,
                documentService,
                documentRepository,
                chunkRepository,
                ingestionService,
                ingestionJobRepository,
                retrieverService,
                dbBranch
        );
        when(securityContextResolver.resolve(principal)).thenReturn(context);
        when(chunkRepository.countByDocumentId(any(UUID.class))).thenReturn(0);
        when(ingestionJobRepository.findLatestByDocumentId(any(UUID.class))).thenReturn(Optional.empty());
    }

    @Test
    void platformAdminWithoutTenantListsGlobalDocuments() {
        Principal platformPrincipal = () -> "admin : SupportAdmin";
        UUID documentId = UUID.randomUUID();
        when(securityContextResolver.resolve(platformPrincipal)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND,
                "TENANT_CONTEXT_NOT_FOUND",
                "Could not resolve tenant context"
        ));
        when(platformAuthorizationService.requirePlatformCapability(platformPrincipal.getName(), "platform.admin.read"))
                .thenReturn(platformUser());
        when(documentRepository.listScoped(eq(null), eq("help"), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(List.of(document(documentId, null, null, "ACTIVE")));
        when(documentRepository.countScoped(eq(null), eq("help"), eq(null), eq(null), eq(null)))
                .thenReturn(1L);

        AiKnowledgeDocumentListResponse response = service.listDocuments(
                platformPrincipal,
                "help",
                null,
                null,
                null,
                null,
                0,
                20
        );

        assertEquals(1, response.total());
        assertEquals(documentId, response.items().get(0).id());
        verify(platformAuthorizationService).requirePlatformCapability(platformPrincipal.getName(), "platform.admin.read");
    }

    @Test
    void createDocumentUsesSecurityCompanyIdAndUserId() {
        UUID documentId = UUID.randomUUID();
        when(documentService.createDocument(any(AiKnowledgeDocumentRecord.class)))
                .thenReturn(document(documentId, 10L, 100L, "DRAFT"));

        service.createDocument(principal, createRequest(false));

        ArgumentCaptor<AiKnowledgeDocumentRecord> captor = ArgumentCaptor.forClass(AiKnowledgeDocumentRecord.class);
        verify(documentService).createDocument(captor.capture());
        AiKnowledgeDocumentRecord saved = captor.getValue();
        assertEquals(10L, saved.companyId());
        assertEquals(77L, saved.createdByUserId());
        assertEquals(77L, saved.updatedByUserId());
        assertEquals(100L, saved.branchId());
        verify(permissionService).validateAdminAccess(context);
        verify(permissionService).validateBranchAccess(context, 100L);
    }

    @Test
    void createDocumentRejectsBlankTitleAndContent() {
        ApiException titleException = assertThrows(
                ApiException.class,
                () -> service.createDocument(principal, new AiKnowledgeDocumentCreateRequest(
                        " ",
                        "USER_MANUAL",
                        "help",
                        null,
                        null,
                        "en",
                        "MANUAL",
                        null,
                        "content",
                        false
                ))
        );
        assertEquals(HttpStatus.BAD_REQUEST, titleException.getStatus());
        assertEquals("KNOWLEDGE_DOCUMENT_TITLE_REQUIRED", titleException.getCode());

        ApiException contentException = assertThrows(
                ApiException.class,
                () -> service.createDocument(principal, new AiKnowledgeDocumentCreateRequest(
                        "Title",
                        "USER_MANUAL",
                        "help",
                        null,
                        null,
                        "en",
                        "MANUAL",
                        null,
                        " ",
                        false
                ))
        );
        assertEquals("KNOWLEDGE_DOCUMENT_CONTENT_REQUIRED", contentException.getCode());
        verify(documentService, never()).createDocument(any());
    }

    @Test
    void ingestEndpointCallsIngestionService() {
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(documentId, 10L, null, "DRAFT")));
        when(ingestionService.ingest(documentId)).thenReturn(job(jobId, documentId, "SUCCEEDED"));

        AiKnowledgeIngestResponse response = service.ingestDocument(principal, documentId);

        verify(ingestionService).ingest(documentId);
        assertEquals(jobId, response.ingestionJob().jobId());
        assertEquals(documentId, response.document().id());
    }

    @Test
    void searchTestCallsRetrieverWithBackendCompanyAndBranches() {
        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(retrieverService.retrieve(any(AiRetrievalRequest.class))).thenReturn(List.of(new AiRetrievedChunk(
                chunkId,
                documentId,
                "Manual",
                10L,
                100L,
                "help",
                "en",
                "Returns",
                "Raw content should not be returned as metadata.",
                "Preview only",
                0.91,
                "MANUAL",
                "internal://manual",
                Map.of("secret", "should-not-leak"),
                "VECTOR"
        )));

        AiKnowledgeSearchTestResponse response = service.searchTest(principal, new AiKnowledgeSearchTestRequest(
                "How do I return a rented item?",
                null,
                100L,
                "help",
                "en",
                5,
                0.72
        ));

        ArgumentCaptor<AiRetrievalRequest> captor = ArgumentCaptor.forClass(AiRetrievalRequest.class);
        verify(retrieverService).retrieve(captor.capture());
        AiRetrievalRequest request = captor.getValue();
        assertEquals(10L, request.companyId());
        assertEquals(Set.of(100L, 101L), request.allowedBranchIds());
        assertEquals(100L, request.selectedBranchId());
        assertEquals(Set.of("help"), request.allowedModules());

        assertEquals(1, response.items().size());
        assertEquals("Preview only", response.items().get(0).contentPreview());
        assertFalse(response.toString().contains("should-not-leak"));
    }

    @Test
    void unauthorizedBranchIsRejected() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED", "Branch access denied"))
                .when(permissionService).validateBranchAccess(context, 999L);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.searchTest(principal, new AiKnowledgeSearchTestRequest(
                        "question",
                        null,
                        999L,
                        "help",
                        "en",
                        5,
                        0.72
                ))
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("BRANCH_ACCESS_DENIED", exception.getCode());
        verify(retrieverService, never()).retrieve(any());
    }

    @Test
    void embeddingDisabledDuringIngestReturnsCleanError() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(documentId, 10L, null, "DRAFT")));
        when(ingestionService.ingest(documentId)).thenThrow(new AiKnowledgeIngestionException(
                "Knowledge ingestion failed.",
                new AiEmbeddingException("AI embeddings are disabled. Set vls.ai.embedding.enabled=true.")
        ));

        ApiException exception = assertThrows(ApiException.class, () -> service.ingestDocument(principal, documentId));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("AI_EMBEDDING_DISABLED", exception.getCode());
        assertEquals("AI embeddings are disabled", exception.getMessage());
    }

    @Test
    void listDocumentsRespectsCompanyScope() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.listScoped(eq(10L), eq("help"), eq("ACTIVE"), eq(100L), eq("en"), eq(0), eq(20)))
                .thenReturn(List.of(document(documentId, 10L, 100L, "ACTIVE")));
        when(documentRepository.countScoped(eq(10L), eq("help"), eq("ACTIVE"), eq(100L), eq("en")))
                .thenReturn(1L);

        AiKnowledgeDocumentListResponse response = service.listDocuments(
                principal,
                "help",
                "ACTIVE",
                null,
                100L,
                "en",
                0,
                20
        );

        assertEquals(1, response.total());
        assertEquals(documentId, response.items().get(0).id());
        verify(documentRepository, never()).listScoped(eq(null), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    void documentDtoDoesNotExposeMetadataOrEmbeddings() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(new AiKnowledgeDocumentRecord(
                documentId,
                10L,
                null,
                "help",
                "en",
                "USER_MANUAL",
                "Manual",
                "MANUAL",
                null,
                "hash",
                "secret raw token=abc123abc123abc123abc123",
                "Normalized safe content",
                "ACTIVE",
                "{\"secret\":\"value\"}",
                77L,
                77L,
                Instant.now(),
                Instant.now()
        )));

        String responseText = service.getDocument(principal, documentId).toString();

        assertFalse(responseText.contains("metadata"));
        assertFalse(responseText.contains("embedding"));
        assertFalse(responseText.contains("abc123abc123abc123abc123"));
        assertNull(service.getDocument(principal, documentId).latestIngestionJob());
    }

    private AiKnowledgeDocumentCreateRequest createRequest(boolean ingestNow) {
        return new AiKnowledgeDocumentCreateRequest(
                "Inventory User Manual",
                "USER_MANUAL",
                "help",
                null,
                100L,
                "en",
                "MANUAL",
                null,
                "Use this manual for inventory workflows.",
                ingestNow
        );
    }

    private AiKnowledgeDocumentRecord document(UUID id, Long companyId, Long branchId, String status) {
        return new AiKnowledgeDocumentRecord(
                id,
                companyId,
                branchId,
                "help",
                "en",
                "USER_MANUAL",
                "Inventory User Manual",
                "MANUAL",
                null,
                "hash",
                "Use this manual for inventory workflows.",
                "Use this manual for inventory workflows.",
                status,
                "{}",
                77L,
                77L,
                Instant.now(),
                Instant.now()
        );
    }

    private AiKnowledgeIngestionJobRecord job(UUID jobId, UUID documentId, String status) {
        return new AiKnowledgeIngestionJobRecord(
                jobId,
                documentId,
                10L,
                null,
                status,
                "gemini-embedding-2",
                3,
                null,
                "{}",
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private User platformUser() {
        return new User(
                99,
                "admin",
                "",
                "admin@example.com",
                "Admin",
                "User",
                "",
                "SupportAdmin",
                0,
                0,
                null
        );
    }
}
