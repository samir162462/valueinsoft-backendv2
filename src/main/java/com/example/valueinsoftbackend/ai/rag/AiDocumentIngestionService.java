package com.example.valueinsoftbackend.ai.rag;

import org.springframework.stereotype.Service;

@Service
public class AiDocumentIngestionService {

    private final AiDocumentRepository documentRepository;
    private final AiDocumentChunkRepository chunkRepository;
    private final AiDocumentChunkingService chunkingService;

    public AiDocumentIngestionService(AiDocumentRepository documentRepository,
                                      AiDocumentChunkRepository chunkRepository,
                                      AiDocumentChunkingService chunkingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
    }

    public void ingest(AiDocumentRecord document) {
        documentRepository.upsert(document);
        chunkRepository.replaceChunks(document.id(), chunkingService.chunk(document));
    }
}
