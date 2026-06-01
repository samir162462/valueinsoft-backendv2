package com.example.valueinsoftbackend.ai.knowledge;

import java.util.Set;

public record AiRetrievalRequest(
        Long companyId,
        Set<Long> allowedBranchIds,
        Long selectedBranchId,
        Set<String> allowedModules,
        String language,
        String query,
        Integer topK,
        Double similarityThreshold,
        boolean allowGlobalDocs,
        boolean allowKeywordFallback
) {
}
