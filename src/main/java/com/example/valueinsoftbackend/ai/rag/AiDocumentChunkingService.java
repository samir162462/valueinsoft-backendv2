package com.example.valueinsoftbackend.ai.rag;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiDocumentChunkingService {

    private static final int TARGET_WORDS = 750;
    private static final int OVERLAP_WORDS = 120;

    public List<AiDocumentChunkRecord> chunk(AiDocumentRecord document) {
        String content = document.content() == null ? "" : document.content().trim();
        if (content.isBlank()) {
            return List.of();
        }

        String[] words = content.split("\\s+");
        List<AiDocumentChunkRecord> chunks = new ArrayList<>();
        int index = 0;
        int chunkIndex = 0;

        while (index < words.length) {
            int end = Math.min(words.length, index + TARGET_WORDS);
            String chunkContent = String.join(" ", java.util.Arrays.copyOfRange(words, index, end));
            chunks.add(new AiDocumentChunkRecord(
                    UUID.randomUUID(),
                    document.id(),
                    document.companyId(),
                    document.title(),
                    document.module(),
                    document.language(),
                    chunkIndex,
                    chunkContent,
                    document.metadataJson(),
                    Instant.now()
            ));

            if (end >= words.length) {
                break;
            }
            index = Math.max(end - OVERLAP_WORDS, index + 1);
            chunkIndex++;
        }

        return chunks;
    }
}
