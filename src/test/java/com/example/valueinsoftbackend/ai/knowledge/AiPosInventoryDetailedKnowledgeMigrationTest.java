package com.example.valueinsoftbackend.ai.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPosInventoryDetailedKnowledgeMigrationTest {

    private static final String MIGRATION =
            "db/migration/V165__ai_pos_inventory_detailed_knowledge.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "\\('(?<document>41000000-0000-0000-0000-0000000000\\d{2})',\\s*'[^']+',\\s*'[^']+',\\s*(?<chunk>\\d+),"
    );

    @Test
    void seedsDetailedPosAndInventoryKnowledgeIntoBothRagStores() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION);
        assertTrue(resource.exists());
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        Matcher matcher = SEED_ROW.matcher(sql);
        Set<String> documentIds = new HashSet<>();
        int chunks = 0;
        while (matcher.find()) {
            documentIds.add(matcher.group("document"));
            chunks++;
        }

        assertEquals(33, documentIds.size());
        assertEquals(99, chunks);
        assertTrue(sql.contains("INSERT INTO public.ai_document\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_document_chunk\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_knowledge_document\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_knowledge_chunk\n"));
        assertTrue(sql.contains("rag-pos-inventory-detail-v1"));
        assertTrue(sql.contains("'keyword-fallback'"));
        assertTrue(sql.contains("embeddingStatus"));
    }

    @Test
    void coversImplementedPosAndInventoryRulesAndBilingualTerms() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        for (String requiredCoverage : Set.of(
                "POS_PARTIAL_PAYMENT_REQUIRES_CREDIT",
                "pos.shift.force_close",
                "NEEDS_REVIEW",
                "Serialized and IMEI sale lines must be posted online",
                "inventory.lowStockThreshold",
                "RESERVED_QUANTITY_CONFLICT",
                "SERIALIZED_UNIT_COUNT_MISMATCH",
                "IMPORT_BATCH_NOT_READY",
                "IMPORTED_WITH_ERRORS",
                "inventory.pricing.adjustment.approve",
                "Maker-checker",
                "نقطة البيع",
                "المخزون",
                "رقم IMEI"
        )) {
            assertTrue(sql.contains(requiredCoverage), "Missing detailed RAG coverage for " + requiredCoverage);
        }
    }
}
