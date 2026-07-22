package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingException;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingResult;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingService;
import com.example.valueinsoftbackend.ai.rag.AiDocumentChunkRecord;
import com.example.valueinsoftbackend.ai.rag.AiKnowledgeSearchResult;
import com.example.valueinsoftbackend.ai.rag.AiKnowledgeSearchService;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRetrieverServiceTest {

    private AiProperties properties;
    private AiEmbeddingService embeddingService;
    private AiKnowledgeChunkRepository chunkRepository;
    private AiKnowledgeSearchService keywordSearchService;
    private AiModelClient modelClient;
    private AiRetrieverService service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.getRag().setEnabled(true);
        embeddingService = mock(AiEmbeddingService.class);
        chunkRepository = mock(AiKnowledgeChunkRepository.class);
        keywordSearchService = mock(AiKnowledgeSearchService.class);
        modelClient = mock(AiModelClient.class);
        service = new AiRetrieverService(properties, embeddingService, chunkRepository, keywordSearchService, modelClient);
    }

    @Test
    void usesDefaultsAndPassesQueryEmbeddingToRepository() {
        float[] queryVector = vector();
        AiRetrievedChunk chunk = vectorChunk(0.88);
        when(embeddingService.embedQuery("how to add product"))
                .thenReturn(new AiEmbeddingResult(0, "how to add product", queryVector, "test", "test-embedding", 768));
        when(chunkRepository.vectorSearch(any(), eq(queryVector))).thenReturn(List.of(chunk));

        List<AiRetrievedChunk> results = service.retrieve(request(null, null));

        assertEquals(List.of(chunk), results);
        ArgumentCaptor<AiRetrievalRequest> captor = ArgumentCaptor.forClass(AiRetrievalRequest.class);
        verify(chunkRepository).vectorSearch(captor.capture(), eq(queryVector));
        assertEquals(5, captor.getValue().topK());
        assertEquals(0.60, captor.getValue().similarityThreshold());
        assertEquals("en", captor.getValue().language());
    }

    @Test
    void returnsEmptyListWhenVectorSearchReturnsNoChunks() {
        float[] queryVector = vector();
        when(embeddingService.embedQuery(anyString()))
                .thenReturn(new AiEmbeddingResult(0, "query", queryVector, "test", "test-embedding", 768));
        when(chunkRepository.vectorSearch(any(), eq(queryVector))).thenReturn(List.of());
        when(keywordSearchService.search(eq(1L), eq("en"), eq(Set.of("inventory")), anyString(), eq(3)))
                .thenReturn(List.of());

        assertEquals(List.of(), service.retrieve(request(3, 0.8)));
        verify(keywordSearchService).search(eq(1L), eq("en"), eq(Set.of("inventory")), anyString(), eq(3));
    }

    @Test
    void fallsBackToGroundedKeywordSearchWhenVectorCorpusHasNoEmbeddedMatches() {
        float[] queryVector = vector();
        when(embeddingService.embedQuery(anyString()))
                .thenReturn(new AiEmbeddingResult(0, "query", queryVector, "test", "test-embedding", 768));
        when(chunkRepository.vectorSearch(any(), eq(queryVector))).thenReturn(List.of());
        when(keywordSearchService.search(
                eq(1L), eq("en"), eq(Set.of("help", "dashboard")), contains("Company dashboard"), eq(5)))
                .thenReturn(List.of(keywordResult()));

        AiRetrievalRequest pageExplanationRequest = new AiRetrievalRequest(
                1L,
                Set.of(10L),
                null,
                Set.of("help", "dashboard"),
                "en",
                "Explain the current ValueInSoft page in Arabic. Page: Company dashboard. View ID: CompanyDashboardPage.",
                5,
                0.6,
                true,
                true
        );

        List<AiRetrievedChunk> results = service.retrieve(pageExplanationRequest);

        assertEquals(1, results.size());
        verify(keywordSearchService).search(
                eq(1L), eq("en"), eq(Set.of("help", "dashboard")), contains("Company dashboard"), eq(5));
    }

    @Test
    void fallsBackToKeywordSearchWhenVectorFailsAndFallbackEnabled() {
        float[] queryVector = vector();
        when(embeddingService.embedQuery(anyString()))
                .thenReturn(new AiEmbeddingResult(0, "query", queryVector, "test", "test-embedding", 768));
        when(chunkRepository.vectorSearch(any(), eq(queryVector))).thenThrow(
                new BadSqlGrammarException("vector", "select", new SQLException("type \"vector\" does not exist"))
        );
        when(keywordSearchService.search(
                eq(1L), eq("en"), eq(Set.of("inventory")), anyString(), eq(5)))
                .thenReturn(List.of(keywordResult()));

        List<AiRetrievedChunk> results = service.retrieve(request(null, null));

        assertEquals(1, results.size());
        assertEquals("KEYWORD_FALLBACK", results.get(0).retrievalType());
        assertEquals("Keyword Help", results.get(0).documentTitle());
    }

    @Test
    void fallsBackToKeywordSearchWhenEmbeddingsAreDisabled() {
        when(embeddingService.embedQuery(anyString())).thenThrow(
                new AiEmbeddingException("AI embeddings are disabled. Set vls.ai.embedding.enabled=true.")
        );
        when(keywordSearchService.search(
                eq(1L), eq("en"), eq(Set.of("inventory")), anyString(), eq(5)))
                .thenReturn(List.of(keywordResult()));

        List<AiRetrievedChunk> results = service.retrieve(request(null, null));

        assertEquals(1, results.size());
        verify(chunkRepository, never()).vectorSearch(any(), any());
    }

    @Test
    void keywordInfrastructureFailureIsNotMisreportedAsNoMatch() {
        when(embeddingService.embedQuery(anyString())).thenThrow(
                new AiEmbeddingException("embedding provider unavailable")
        );
        BadSqlGrammarException databaseFailure = new BadSqlGrammarException(
                "keyword",
                "select",
                new SQLException("indeterminate datatype")
        );
        when(keywordSearchService.search(
                eq(1L), eq("en"), eq(Set.of("inventory")), anyString(), eq(5)))
                .thenThrow(databaseFailure);

        BadSqlGrammarException exception = assertThrows(
                BadSqlGrammarException.class,
                () -> service.retrieve(request(null, null))
        );

        assertEquals(databaseFailure, exception);
    }

    @Test
    void expandsArabicQuestionWithRealModelBeforeKeywordFallback() {
        when(embeddingService.embedQuery(anyString())).thenThrow(
                new AiEmbeddingException("embedding provider unavailable")
        );
        when(modelClient.generate(any())).thenReturn(
                new AiModelResponse("add product inventory", "deepseek-chat", false, "deepseek", "DS")
        );
        when(keywordSearchService.search(
                eq(1L), eq("ar"), eq(Set.of("inventory")), anyString(), eq(5)))
                .thenReturn(List.of(keywordResult()));

        AiRetrievalRequest arabicRequest = new AiRetrievalRequest(
                1L, Set.of(10L), 10L, Set.of("inventory"), "ar", "كيف أضيف منتج؟",
                5, 0.6, true, true
        );
        List<AiRetrievedChunk> results = service.retrieve(arabicRequest);

        assertEquals(1, results.size());
        verify(modelClient).generate(any());
        verify(keywordSearchService).search(
                eq(1L),
                eq("ar"),
                eq(Set.of("inventory")),
                org.mockito.ArgumentMatchers.argThat(query -> query.contains("add product inventory")),
                eq(5));
    }

    @Test
    void doesNotFallbackForInvalidRequest() {
        AiRetrievalRequest invalid = new AiRetrievalRequest(
                null,
                Set.of(),
                null,
                Set.of("inventory"),
                "en",
                "query",
                null,
                null,
                false,
                true
        );

        assertThrows(AiKnowledgeIngestionException.class, () -> service.retrieve(invalid));
        verify(keywordSearchService, never()).search(any(), any(), any(), anyString(), anyInt());
    }

    private AiRetrievalRequest request(Integer topK, Double threshold) {
        return new AiRetrievalRequest(
                1L,
                Set.of(10L, 11L),
                10L,
                Set.of("inventory"),
                null,
                "how to add product",
                topK,
                threshold,
                true,
                true
        );
    }

    private AiRetrievedChunk vectorChunk(double similarity) {
        return new AiRetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Inventory Manual",
                1L,
                10L,
                "inventory",
                "en",
                "Add Product",
                "Add a product from inventory screen.",
                "Add a product from inventory screen.",
                similarity,
                "USER_MANUAL",
                null,
                Map.of(),
                "VECTOR"
        );
    }

    private AiKnowledgeSearchResult keywordResult() {
        return new AiKnowledgeSearchResult(
                new AiDocumentChunkRecord(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1L,
                        "Keyword Help",
                        "inventory",
                        "en",
                        0,
                        "Add products from the Inventory screen.",
                        "{}",
                        Instant.now()
                ),
                7
        );
    }

    private float[] vector() {
        return new float[768];
    }
}
