package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ai.config.AiProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiKnowledgeChunkingService {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private final AiProperties aiProperties;

    public AiKnowledgeChunkingService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public List<AiKnowledgeChunkRecord> chunk(AiKnowledgeDocumentRecord document, String normalizedContent) {
        if (normalizedContent == null || normalizedContent.isBlank()) {
            return List.of();
        }
        List<Section> sections = splitSections(normalizedContent);
        List<AiKnowledgeChunkRecord> chunks = new ArrayList<>();
        for (Section section : sections) {
            appendSectionChunks(document, section, chunks);
        }
        return chunks;
    }

    private void appendSectionChunks(AiKnowledgeDocumentRecord document,
                                     Section section,
                                     List<AiKnowledgeChunkRecord> chunks) {
        String[] words = section.content().trim().split("\\s+");
        if (words.length == 1 && words[0].isBlank()) {
            return;
        }
        int target = targetTokens();
        int overlap = overlapTokens(target);
        int index = 0;
        while (index < words.length) {
            int end = Math.min(words.length, index + target);
            String content = String.join(" ", java.util.Arrays.copyOfRange(words, index, end)).trim();
            if (!content.isBlank()) {
                chunks.add(new AiKnowledgeChunkRecord(
                        UUID.randomUUID(),
                        document.id(),
                        document.companyId(),
                        document.branchId(),
                        document.module(),
                        defaultIfBlank(document.language(), "en"),
                        chunks.size(),
                        section.heading(),
                        content,
                        approximateTokenCount(content),
                        null,
                        null,
                        "ACTIVE",
                        document.metadataJson() == null || document.metadataJson().isBlank() ? "{}" : document.metadataJson(),
                        Instant.now()
                ));
            }
            if (end >= words.length) {
                break;
            }
            index = Math.max(index + 1, end - overlap);
        }
    }

    private List<Section> splitSections(String content) {
        String[] lines = content.split("\\R");
        List<Section> sections = new ArrayList<>();
        String currentHeading = null;
        StringBuilder currentContent = new StringBuilder();
        boolean sawHeading = false;

        for (String line : lines) {
            Matcher matcher = MARKDOWN_HEADING.matcher(line.trim());
            if (matcher.matches()) {
                sawHeading = true;
                flushSection(sections, currentHeading, currentContent);
                currentHeading = matcher.group(2).trim();
                currentContent.append(currentHeading).append('\n');
                continue;
            }
            currentContent.append(line).append('\n');
        }
        flushSection(sections, currentHeading, currentContent);

        if (!sawHeading && sections.isEmpty()) {
            sections.add(new Section(null, content));
        }
        return sections;
    }

    private void flushSection(List<Section> sections, String heading, StringBuilder content) {
        String value = content.toString().trim();
        if (!value.isBlank()) {
            sections.add(new Section(heading, value));
        }
        content.setLength(0);
    }

    private int targetTokens() {
        AiProperties.RagProperties rag = aiProperties.getRag();
        int configured = rag == null ? 700 : rag.getChunkTargetTokens();
        return Math.max(10, configured);
    }

    private int overlapTokens(int target) {
        AiProperties.RagProperties rag = aiProperties.getRag();
        int configured = rag == null ? 120 : rag.getChunkOverlapTokens();
        return Math.max(0, Math.min(configured, target / 2));
    }

    private int approximateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Section(String heading, String content) {
    }
}
