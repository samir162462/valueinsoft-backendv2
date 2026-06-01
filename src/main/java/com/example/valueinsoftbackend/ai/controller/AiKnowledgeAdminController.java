package com.example.valueinsoftbackend.ai.controller;

import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentCreateRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentDto;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentListResponse;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeIngestRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeIngestResponse;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeIngestionJobDto;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeRuntimeStatusDto;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeSearchTestRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeSearchTestResponse;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.knowledge.AiKnowledgeAdminService;
import com.example.valueinsoftbackend.ai.knowledge.AiKnowledgeChunkRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/ai/knowledge")
public class AiKnowledgeAdminController {

    private final AiKnowledgeAdminService adminService;
    private final AiProperties aiProperties;
    private final AiKnowledgeChunkRepository chunkRepository;

    public AiKnowledgeAdminController(AiKnowledgeAdminService adminService,
                                      AiProperties aiProperties,
                                      AiKnowledgeChunkRepository chunkRepository) {
        this.adminService = adminService;
        this.aiProperties = aiProperties;
        this.chunkRepository = chunkRepository;
    }

    @GetMapping("/status")
    public AiKnowledgeRuntimeStatusDto runtimeStatus() {
        AiProperties.EmbeddingProperties embedding = aiProperties.getEmbedding();
        AiProperties.GoogleEmbeddingProperties google = embedding == null ? null : embedding.getGoogle();
        boolean embeddingEnabled = embedding != null && embedding.isEnabled();
        String provider = embedding == null || embedding.getProvider() == null || embedding.getProvider().isBlank()
                ? "google"
                : embedding.getProvider().trim();
        String model = embedding == null || embedding.getModel() == null || embedding.getModel().isBlank()
                ? "gemini-embedding-2"
                : embedding.getModel().trim();
        int dimension = embedding == null ? 768 : embedding.getDimension();
        boolean apiKeyConfigured = google != null && google.getApiKey() != null && !google.getApiKey().isBlank();
        boolean vectorColumnReady = chunkRepository.hasVectorColumn();
        boolean keywordFallbackEnabled = aiProperties.getRag() != null && aiProperties.getRag().isKeywordFallbackEnabled();
        String ingestionStatus = (embeddingEnabled && apiKeyConfigured) || keywordFallbackEnabled ? "READY" : "BLOCKED";
        String ingestionMessage;
        if (!embeddingEnabled) {
            ingestionMessage = keywordFallbackEnabled
                    ? "Embeddings are disabled. Ingestion will store keyword-only chunks; enable embeddings later for semantic vector search."
                    : "Embeddings are disabled. Set VLS_AI_EMBEDDING_ENABLED=true and configure a backend API key before ingesting documents.";
        } else if (!apiKeyConfigured) {
            ingestionMessage = keywordFallbackEnabled
                    ? "Embedding API key is missing. Ingestion will store keyword-only chunks; configure the backend key later for semantic vector search."
                    : "Embedding API key is missing. Configure VLS_AI_EMBEDDING_GOOGLE_API_KEY, GOOGLE_AI_API_KEY, or GEMINI_API_KEY on the backend.";
        } else if (dimension != 768) {
            ingestionMessage = "Embedding dimension must remain 768 for the current pgvector schema.";
            ingestionStatus = "BLOCKED";
        } else if (!vectorColumnReady) {
            ingestionMessage = "Embeddings are ready, but pgvector is not installed in this database. Ingestion will store vectors as an array fallback; install pgvector and rerun the migration for semantic vector search.";
        } else {
            ingestionMessage = "Knowledge ingestion is ready.";
        }
        return new AiKnowledgeRuntimeStatusDto(
                aiProperties.isRagEnabled(),
                embeddingEnabled,
                provider,
                model,
                dimension,
                apiKeyConfigured,
                ingestionStatus,
                ingestionMessage
        );
    }

    @PostMapping("/documents")
    public AiKnowledgeIngestResponse createDocument(Principal principal,
                                                    @RequestBody AiKnowledgeDocumentCreateRequest request) {
        return adminService.createDocument(principal, request);
    }

    @GetMapping("/documents")
    public AiKnowledgeDocumentListResponse listDocuments(Principal principal,
                                                         @RequestParam(required = false) String module,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Long companyId,
                                                         @RequestParam(required = false) Long branchId,
                                                         @RequestParam(required = false) String language,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return adminService.listDocuments(principal, module, status, companyId, branchId, language, page, size);
    }

    @GetMapping("/documents/{documentId}")
    public AiKnowledgeDocumentDto getDocument(Principal principal,
                                              @PathVariable UUID documentId) {
        return adminService.getDocument(principal, documentId);
    }

    @PostMapping("/documents/{documentId}/ingest")
    public AiKnowledgeIngestResponse ingestDocument(Principal principal,
                                                    @PathVariable UUID documentId,
                                                    @RequestBody(required = false) AiKnowledgeIngestRequest request) {
        return adminService.ingestDocument(principal, documentId);
    }

    @GetMapping("/ingestion-jobs/{jobId}")
    public AiKnowledgeIngestionJobDto getIngestionJob(Principal principal,
                                                      @PathVariable UUID jobId) {
        return adminService.getIngestionJob(principal, jobId);
    }

    @PostMapping("/search-test")
    public AiKnowledgeSearchTestResponse searchTest(Principal principal,
                                                    @RequestBody AiKnowledgeSearchTestRequest request) {
        return adminService.searchTest(principal, request);
    }
}
