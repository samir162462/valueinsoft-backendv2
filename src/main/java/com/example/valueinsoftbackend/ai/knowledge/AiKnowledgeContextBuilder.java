package com.example.valueinsoftbackend.ai.knowledge;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AiKnowledgeContextBuilder {

    private static final int DEFAULT_MAX_CHUNK_CHARS = 1_200;

    public String buildContext(List<AiRetrievedChunk> chunks) {
        return buildContext(chunks, DEFAULT_MAX_CHUNK_CHARS);
    }

    public String buildContext(List<AiRetrievedChunk> chunks, int maxChunkChars) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        int chunkLimit = Math.max(200, maxChunkChars);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            AiRetrievedChunk chunk = chunks.get(index);
            if (chunk == null || chunk.content() == null || chunk.content().isBlank()) {
                continue;
            }
            builder.append("[Source ").append(index + 1).append("]\n");
            builder.append("Title: ").append(blankToUnknown(chunk.documentTitle())).append('\n');
            if (chunk.module() != null && !chunk.module().isBlank()) {
                builder.append("Module: ").append(chunk.module()).append('\n');
            }
            builder.append("Similarity: ").append(String.format(Locale.ROOT, "%.2f", chunk.similarity())).append('\n');
            builder.append("Content:\n").append(truncate(chunk.content(), chunkLimit)).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String truncate(String content, int maxChars) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 3)).trim() + "...";
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "Untitled knowledge source" : value.trim();
    }
}
