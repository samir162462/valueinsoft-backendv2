package com.example.valueinsoftbackend.ai.knowledge;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AiKnowledgeDocumentService {

    private final AiKnowledgeDocumentRepository documentRepository;
    private final AiKnowledgeContentCleaner contentCleaner;
    private final AiKnowledgeIngestionService ingestionService;

    public AiKnowledgeDocumentService(AiKnowledgeDocumentRepository documentRepository,
                                      AiKnowledgeContentCleaner contentCleaner,
                                      AiKnowledgeIngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.contentCleaner = contentCleaner;
        this.ingestionService = ingestionService;
    }

    public AiKnowledgeDocumentRecord createDocument(AiKnowledgeDocumentRecord request) {
        String sourceContent = firstNonBlank(request.rawContent(), request.normalizedContent());
        String normalizedContent = contentCleaner.clean(sourceContent);
        if (normalizedContent.isBlank()) {
            throw new AiKnowledgeIngestionException("Knowledge document content must not be blank.");
        }
        AiKnowledgeDocumentRecord document = new AiKnowledgeDocumentRecord(
                request.id() == null ? UUID.randomUUID() : request.id(),
                request.companyId(),
                request.branchId(),
                request.module(),
                defaultIfBlank(request.language(), "en"),
                defaultIfBlank(request.documentType(), "HELP_ARTICLE"),
                request.title(),
                defaultIfBlank(request.sourceType(), "MANUAL"),
                request.sourceUri(),
                request.contentHash(),
                request.rawContent(),
                normalizedContent,
                defaultIfBlank(request.status(), "DRAFT"),
                defaultIfBlank(request.metadataJson(), "{}"),
                request.createdByUserId(),
                request.updatedByUserId(),
                request.createdAt() == null ? Instant.now() : request.createdAt(),
                request.updatedAt() == null ? Instant.now() : request.updatedAt()
        );
        return documentRepository.insert(document);
    }

    public AiKnowledgeIngestionJobRecord ingest(UUID documentId) {
        return ingestionService.ingest(documentId);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
