package com.example.valueinsoftbackend.ai.rag;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AiKnowledgeSearchService {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "can", "do", "does", "for", "from", "how", "i", "in",
            "is", "it", "me", "of", "on", "or", "the", "to", "use", "what", "with"
    );

    private final AiDocumentChunkRepository chunkRepository;

    public AiKnowledgeSearchService(AiDocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public List<AiKnowledgeSearchResult> search(Long companyId, String question, int limit) {
        return search(companyId, null, Set.of(), question, limit);
    }

    public List<AiKnowledgeSearchResult> search(Long companyId,
                                                String language,
                                                Set<String> allowedModules,
                                                String question,
                                                int limit) {
        Set<String> terms = tokenize(question);
        if (terms.isEmpty()) {
            return List.of();
        }

        Set<String> normalizedModules = allowedModules == null
                ? Set.of()
                : allowedModules.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return chunkRepository.findActiveChunks(companyId, language, 500)
                .stream()
                .filter(chunk -> normalizedModules.isEmpty()
                        || chunk.module() == null
                        || normalizedModules.contains(chunk.module().trim().toLowerCase(Locale.ROOT)))
                .map(chunk -> new AiKnowledgeSearchResult(chunk, score(chunk, terms)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(AiKnowledgeSearchResult::score).reversed())
                .limit(Math.max(1, Math.min(limit, 5)))
                .toList();
    }

    private int score(AiDocumentChunkRecord chunk, Set<String> terms) {
        String haystack = (chunk.title() + " " + chunk.module() + " " + chunk.content()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += chunk.title().toLowerCase(Locale.ROOT).contains(term) ? 3 : 1;
            }
        }
        return score;
    }

    private Set<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+"))
                .map(String::trim)
                .filter(token -> token.length() >= (isAscii(token) ? 3 : 2))
                .filter(token -> !STOP_WORDS.contains(token))
                .forEach(tokens::add);
        return tokens;
    }

    private boolean isAscii(String token) {
        return token.chars().allMatch(character -> character < 128);
    }
}
