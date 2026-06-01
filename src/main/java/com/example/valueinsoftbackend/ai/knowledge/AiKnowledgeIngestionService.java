package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingResult;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingService;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AiKnowledgeIngestionService {

    private final AiKnowledgeDocumentRepository documentRepository;
    private final AiKnowledgeChunkRepository chunkRepository;
    private final AiKnowledgeIngestionJobRepository ingestionJobRepository;
    private final AiKnowledgeContentCleaner contentCleaner;
    private final AiKnowledgeChunkingService chunkingService;
    private final AiEmbeddingService embeddingService;
    private final TransactionTemplate transactionTemplate;

    public AiKnowledgeIngestionService(AiKnowledgeDocumentRepository documentRepository,
                                       AiKnowledgeChunkRepository chunkRepository,
                                       AiKnowledgeIngestionJobRepository ingestionJobRepository,
                                       AiKnowledgeContentCleaner contentCleaner,
                                       AiKnowledgeChunkingService chunkingService,
                                       AiEmbeddingService embeddingService,
                                       TransactionTemplate transactionTemplate) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.contentCleaner = contentCleaner;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.transactionTemplate = transactionTemplate;
    }

    public AiKnowledgeIngestionJobRecord ingest(UUID documentId) {
        AiKnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AiKnowledgeIngestionException("Knowledge document not found."));
        AiKnowledgeIngestionJobRecord job = ingestionJobRepository.create(
                document.id(),
                document.companyId(),
                document.branchId(),
                "{}"
        );
        String originalStatus = document.status();

        try {
            ingestionJobRepository.markStarted(job.id(), null);
            documentRepository.updateStatus(document.id(), "INGESTING");

            String normalizedContent = contentCleaner.clean(firstNonBlank(document.rawContent(), document.normalizedContent()));
            if (normalizedContent.isBlank()) {
                throw new AiKnowledgeIngestionException("Knowledge document content must not be blank.");
            }

            String contentHash = sha256(normalizedContent);
            List<AiKnowledgeChunkRecord> drafts = chunkingService.chunk(document, normalizedContent);
            if (drafts.isEmpty()) {
                throw new AiKnowledgeIngestionException("Knowledge document produced no chunks.");
            }

            IngestionChunks ingestionChunks = prepareChunks(drafts);
            List<AiKnowledgeChunkRecord> chunks = ingestionChunks.chunks();
            String embeddingModel = ingestionChunks.embeddingModel();
            boolean keywordOnly = ingestionChunks.keywordOnly();

            transactionTemplate.executeWithoutResult(status -> {
                chunkRepository.deleteByDocumentId(document.id());
                if (keywordOnly) {
                    chunkRepository.batchInsertKeywordOnly(chunks);
                } else {
                    chunkRepository.batchInsert(chunks);
                }
                documentRepository.updateContentHash(document.id(), contentHash, normalizedContent);
                documentRepository.updateStatus(document.id(), "ACTIVE");
                ingestionJobRepository.markSucceeded(job.id(), embeddingModel, chunks.size());
            });

            return ingestionJobRepository.findById(job.id()).orElseThrow();
        } catch (RuntimeException exception) {
            ingestionJobRepository.markFailed(job.id(), safeError(exception));
            if ("ACTIVE".equals(originalStatus)) {
                documentRepository.updateStatus(document.id(), "ACTIVE");
            } else {
                documentRepository.updateStatus(document.id(), "FAILED");
            }
            throw exception instanceof AiKnowledgeIngestionException
                    ? exception
                    : new AiKnowledgeIngestionException("Knowledge ingestion failed.", exception);
        }
    }

    private IngestionChunks prepareChunks(List<AiKnowledgeChunkRecord> drafts) {
        try {
            List<AiEmbeddingResult> embeddings = embeddingService.embed(drafts.stream()
                    .map(AiKnowledgeChunkRecord::content)
                    .toList());
            return new IngestionChunks(withEmbeddings(drafts, embeddings), embeddings.get(0).model(), false);
        } catch (AiEmbeddingException exception) {
            return new IngestionChunks(withoutEmbeddings(drafts), "keyword-fallback", true);
        }
    }

    private List<AiKnowledgeChunkRecord> withEmbeddings(List<AiKnowledgeChunkRecord> drafts,
                                                        List<AiEmbeddingResult> embeddings) {
        if (drafts.size() != embeddings.size()) {
            throw new AiKnowledgeIngestionException("Embedding result count does not match chunk count.");
        }
        List<AiKnowledgeChunkRecord> chunks = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            AiKnowledgeChunkRecord draft = drafts.get(index);
            AiEmbeddingResult embedding = embeddings.get(index);
            embeddingService.validateDimension(embedding.vector());
            chunks.add(new AiKnowledgeChunkRecord(
                    draft.id() == null ? UUID.randomUUID() : draft.id(),
                    draft.documentId(),
                    draft.companyId(),
                    draft.branchId(),
                    draft.module(),
                    draft.language(),
                    draft.chunkIndex(),
                    draft.heading(),
                    draft.content(),
                    draft.tokenCount(),
                    embedding.vector(),
                    embedding.model(),
                    "ACTIVE",
                    draft.metadataJson(),
                    draft.createdAt() == null ? Instant.now() : draft.createdAt()
            ));
        }
        return chunks;
    }

    private List<AiKnowledgeChunkRecord> withoutEmbeddings(List<AiKnowledgeChunkRecord> drafts) {
        List<AiKnowledgeChunkRecord> chunks = new ArrayList<>();
        for (AiKnowledgeChunkRecord draft : drafts) {
            chunks.add(new AiKnowledgeChunkRecord(
                    draft.id() == null ? UUID.randomUUID() : draft.id(),
                    draft.documentId(),
                    draft.companyId(),
                    draft.branchId(),
                    draft.module(),
                    draft.language(),
                    draft.chunkIndex(),
                    draft.heading(),
                    draft.content(),
                    draft.tokenCount(),
                    null,
                    "keyword-fallback",
                    "ACTIVE",
                    draft.metadataJson(),
                    draft.createdAt() == null ? Instant.now() : draft.createdAt()
            ));
        }
        return chunks;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AiKnowledgeIngestionException("Could not compute document content hash.", exception);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Knowledge ingestion failed.";
        }
        String normalized = message.replaceAll("(?i)(api[_ -]?key|token|secret|password)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private record IngestionChunks(List<AiKnowledgeChunkRecord> chunks, String embeddingModel, boolean keywordOnly) {
    }
}
