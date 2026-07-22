package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingException;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingResult;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingService;
import com.example.valueinsoftbackend.ai.rag.AiKnowledgeSearchResult;
import com.example.valueinsoftbackend.ai.rag.AiKnowledgeSearchService;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class AiRetrieverService {

    private final AiProperties aiProperties;
    private final AiEmbeddingService embeddingService;
    private final AiKnowledgeChunkRepository chunkRepository;
    private final AiKnowledgeSearchService keywordSearchService;
    private final AiModelClient modelClient;

    public AiRetrieverService(AiProperties aiProperties,
                              AiEmbeddingService embeddingService,
                              AiKnowledgeChunkRepository chunkRepository,
                              AiKnowledgeSearchService keywordSearchService,
                              AiModelClient modelClient) {
        this.aiProperties = aiProperties;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.keywordSearchService = keywordSearchService;
        this.modelClient = modelClient;
    }

    public List<AiRetrievedChunk> retrieve(AiRetrievalRequest request) {
        AiRetrievalRequest normalized = normalizeAndValidate(request);
        if (!ragProperties().isEnabled()) {
            log.info("AI semantic retrieval skipped because RAG is disabled");
            return keywordFallback(normalized);
        }

        try {
            AiEmbeddingResult queryEmbedding = embeddingService.embedQuery(normalized.query());
            embeddingService.validateDimension(queryEmbedding.vector());
            List<AiRetrievedChunk> chunks = chunkRepository.vectorSearch(normalized, queryEmbedding.vector());
            log.info("AI semantic retrieval completed companyId={} selectedBranchId={} modules={} topK={} resultCount={}",
                    normalized.companyId(),
                    normalized.selectedBranchId(),
                    normalized.allowedModules(),
                    normalized.topK(),
                    chunks.size());
            logRetrievedChunks(chunks, "SEMANTIC_VECTOR", normalized.query());
            if (chunks.isEmpty()
                    && normalized.allowKeywordFallback()
                    && ragProperties().isKeywordFallbackEnabled()) {
                log.info("AI semantic retrieval returned no chunks; trying grounded keyword retrieval");
                return keywordFallback(normalized);
            }
            return chunks;
        } catch (AiEmbeddingException exception) {
            if (normalized.allowKeywordFallback() && ragProperties().isKeywordFallbackEnabled()) {
                log.info("AI semantic retrieval using keyword fallback because embedding search failed: {}", safeMessage(exception));
                return keywordFallback(normalized);
            }
            throw exception;
        } catch (DataAccessException exception) {
            if (isVectorSearchUnavailable(exception)) {
                log.info("AI semantic retrieval using keyword fallback because vector search is unavailable: {}", safeMessage(exception));
                return keywordFallback(normalized);
            }
            throw exception;
        }
    }

    private AiRetrievalRequest normalizeAndValidate(AiRetrievalRequest request) {
        if (request == null) {
            throw new AiKnowledgeIngestionException("Retrieval request is required.");
        }
        if (request.query() == null || request.query().isBlank()) {
            throw new AiKnowledgeIngestionException("Retrieval query is required.");
        }
        if (request.companyId() == null && !request.allowGlobalDocs()) {
            throw new AiKnowledgeIngestionException("Company scope is required for tenant retrieval.");
        }
        Set<Long> allowedBranches = request.allowedBranchIds() == null ? Set.of() : Set.copyOf(request.allowedBranchIds());
        if (request.selectedBranchId() != null && !allowedBranches.isEmpty() && !allowedBranches.contains(request.selectedBranchId())) {
            throw new AiKnowledgeIngestionException("Selected branch is outside the allowed retrieval scope.");
        }
        Set<String> allowedModules = request.allowedModules() == null
                ? Set.of()
                : request.allowedModules().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        int topK = request.topK() == null ? ragProperties().getTopK() : request.topK();
        double threshold = request.similarityThreshold() == null
                ? ragProperties().getSimilarityThreshold()
                : request.similarityThreshold();
        String language = request.language() == null || request.language().isBlank()
                ? defaultLanguage()
                : request.language().trim().toLowerCase(Locale.ROOT);

        return new AiRetrievalRequest(
                request.companyId(),
                allowedBranches,
                request.selectedBranchId(),
                allowedModules,
                language,
                request.query().trim(),
                Math.max(1, Math.min(topK, 25)),
                Math.max(0.0, Math.min(threshold, 1.0)),
                request.allowGlobalDocs(),
                request.allowKeywordFallback()
        );
    }

    private List<AiRetrievedChunk> keywordFallback(AiRetrievalRequest request) {
        if (!request.allowKeywordFallback() || !ragProperties().isKeywordFallbackEnabled()) {
            return List.of();
        }
        try {
            String expandedQuery = expandCrossLanguageQuery(request.query());
            List<AiRetrievedChunk> chunks = keywordSearchService.search(
                            request.companyId(),
                            request.language(),
                            request.allowedModules(),
                            expandedQuery,
                            request.topK())
                    .stream()
                    .map(this::fromKeywordResult)
                    .toList();
            logRetrievedChunks(chunks, "KEYWORD_FALLBACK", request.query());
            return chunks;
        } catch (RuntimeException exception) {
            log.warn("AI keyword fallback retrieval failed: {}", safeMessage(exception));
            throw exception;
        }
    }

    private String expandCrossLanguageQuery(String query) {
        if (query == null || query.isBlank() || query.codePoints().allMatch(codePoint -> codePoint < 128)) {
            return query;
        }
        try {
            AiModelResponse response = modelClient.generate(new AiModelRequest(
                    """
                    Convert the user's multilingual ValueInSoft question into concise English search keywords.
                    Return only space-separated keywords, with no explanation, punctuation, markdown, or answer.
                    Preserve important module concepts such as product, inventory, POS, supplier, customer, shift, sales, payment, and receipt.
                    """,
                    query,
                    "RAG_QUERY_EXPANSION",
                    "",
                    "",
                    null
            ));
            String expansion = response == null || response.answer() == null
                    ? ""
                    : response.answer().replaceAll("[^\\p{L}\\p{Nd}\\s-]", " ").replaceAll("\\s+", " ").trim();
            if (expansion.isBlank()) {
                return query;
            }
            log.info("AI RAG multilingual query expansion completed originalLength={} expandedLength={}",
                    query.length(), expansion.length());
            return query + " " + expansion;
        } catch (RuntimeException exception) {
            log.warn("AI RAG multilingual query expansion unavailable; using original query errorType={}",
                    exception.getClass().getSimpleName());
            return query;
        }
    }

    private AiRetrievedChunk fromKeywordResult(AiKnowledgeSearchResult result) {
        String content = result.chunk().content();
        return new AiRetrievedChunk(
                result.chunk().id(),
                result.chunk().documentId(),
                result.chunk().title(),
                result.chunk().companyId(),
                null,
                result.chunk().module(),
                result.chunk().language(),
                null,
                content,
                preview(content),
                Math.min(1.0, Math.max(0.0, result.score() / 10.0)),
                "HELP_ARTICLE",
                null,
                Map.of(),
                "KEYWORD_FALLBACK"
        );
    }

    private boolean isVectorSearchUnavailable(DataAccessException exception) {
        String message = safeMessage(exception).toLowerCase(Locale.ROOT);
        return message.contains("vector")
                || message.contains("operator does not exist")
                || message.contains("type \"vector\"")
                || message.contains("function")
                || message.contains("syntax error");
    }

    private AiProperties.RagProperties ragProperties() {
        AiProperties.RagProperties rag = aiProperties.getRag();
        return rag == null ? new AiProperties.RagProperties() : rag;
    }

    private String defaultLanguage() {
        String language = ragProperties().getDefaultLanguage();
        return language == null || language.isBlank() ? "en" : language.trim().toLowerCase(Locale.ROOT);
    }

    private String preview(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 260) {
            return normalized;
        }
        return normalized.substring(0, 257).trim() + "...";
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private void logRetrievedChunks(List<AiRetrievedChunk> chunks, String retrievalMethod, String query) {
        if (chunks == null || chunks.isEmpty()) {
            log.info("AI RAG retrieval method={} queryLength={} resultCount=0",
                    retrievalMethod, query == null ? 0 : query.length());
            return;
        }
        log.info("AI RAG retrieval method={} queryLength={} resultCount={}",
                retrievalMethod, query == null ? 0 : query.length(), chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            AiRetrievedChunk chunk = chunks.get(i);
            log.debug("AI RAG result position={} similarity={} module={} chunkId={}",
                    i + 1,
                    chunk.similarity(),
                    chunk.module(),
                    chunk.chunkId());
        }
    }
}
