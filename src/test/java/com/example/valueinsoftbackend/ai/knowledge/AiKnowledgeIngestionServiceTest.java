package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingResult;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeIngestionServiceTest {

    private AiKnowledgeDocumentRepository documentRepository;
    private AiKnowledgeChunkRepository chunkRepository;
    private AiKnowledgeIngestionJobRepository jobRepository;
    private AiEmbeddingService embeddingService;
    private AiKnowledgeIngestionService service;
    private UUID documentId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        documentRepository = mock(AiKnowledgeDocumentRepository.class);
        chunkRepository = mock(AiKnowledgeChunkRepository.class);
        jobRepository = mock(AiKnowledgeIngestionJobRepository.class);
        embeddingService = mock(AiEmbeddingService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AiKnowledgeContentCleaner cleaner = new AiKnowledgeContentCleaner();
        AiKnowledgeChunkingService chunkingService = new AiKnowledgeChunkingService(new com.example.valueinsoftbackend.ai.config.AiProperties());

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new AiKnowledgeIngestionService(
                documentRepository,
                chunkRepository,
                jobRepository,
                cleaner,
                chunkingService,
                embeddingService,
                transactionTemplate
        );

        documentId = UUID.randomUUID();
        jobId = UUID.randomUUID();
    }

    @Test
    void ingestsDocumentAndMarksJobSucceeded() {
        AiKnowledgeDocumentRecord document = document("DRAFT");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(jobRepository.create(documentId, 1L, null, "{}")).thenReturn(job("PENDING"));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job("SUCCEEDED")));
        when(embeddingService.embed(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream()
                    .map(text -> new AiEmbeddingResult(0, text, vector(), "test", "test-embedding", 768))
                    .toList();
        });

        service.ingest(documentId);

        verify(jobRepository).markStarted(jobId, null);
        verify(chunkRepository).deleteByDocumentId(documentId);
        verify(chunkRepository).batchInsert(any());
        verify(documentRepository).updateStatus(documentId, "ACTIVE");
        verify(jobRepository).markSucceeded(eq(jobId), eq("test-embedding"), anyInt());
    }

    @Test
    void embeddingFailureMarksJobFailedAndDoesNotDeleteExistingChunks() {
        AiKnowledgeDocumentRecord document = document("ACTIVE");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(jobRepository.create(documentId, 1L, null, "{}")).thenReturn(job("PENDING"));
        doThrow(new RuntimeException("embedding provider unavailable")).when(embeddingService).embed(any());

        assertThrows(AiKnowledgeIngestionException.class, () -> service.ingest(documentId));

        verify(jobRepository).markFailed(eq(jobId), anyString());
        verify(chunkRepository, never()).deleteByDocumentId(documentId);
        verify(chunkRepository, never()).batchInsert(any());
        verify(documentRepository).updateStatus(documentId, "ACTIVE");
    }

    private AiKnowledgeDocumentRecord document(String status) {
        return new AiKnowledgeDocumentRecord(
                documentId,
                1L,
                null,
                "inventory",
                "en",
                "HELP_ARTICLE",
                "Inventory Manual",
                "MANUAL",
                null,
                null,
                "# Add Product\nAdd product name, barcode, category, price, and opening quantity.",
                "# Add Product\nAdd product name, barcode, category, price, and opening quantity.",
                status,
                "{}",
                10L,
                10L,
                Instant.now(),
                Instant.now()
        );
    }

    private AiKnowledgeIngestionJobRecord job(String status) {
        return new AiKnowledgeIngestionJobRecord(
                jobId,
                documentId,
                1L,
                null,
                status,
                "test-embedding",
                1,
                null,
                "{}",
                Instant.now(),
                "SUCCEEDED".equals(status) ? Instant.now() : null,
                Instant.now()
        );
    }

    private float[] vector() {
        return new float[768];
    }
}
