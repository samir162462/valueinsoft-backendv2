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

class AiApplicationKnowledgeMigrationTest {

    private static final String MIGRATION =
            "db/migration/V164__ai_application_rag_knowledge_pack.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "\\('(?<document>40000000-0000-0000-0000-0000000000\\d{2})',\\s*'[^']+',\\s*'[^']+',\\s*(?<chunk>\\d+),"
    );

    @Test
    void seedsCompleteApplicationKnowledgeIntoBothRagStores() throws Exception {
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

        assertEquals(28, documentIds.size());
        assertEquals(84, chunks);
        assertTrue(sql.contains("INSERT INTO public.ai_document\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_document_chunk\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_knowledge_document\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_knowledge_chunk\n"));
        assertTrue(sql.contains("'keyword-fallback'"));
        assertTrue(sql.contains("embeddingStatus"));
    }

    @Test
    void coversEveryPrimaryApplicationAreaAndArabicTerminology() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        for (String requiredCoverage : Set.of(
                "CompanyDashboardPage",
                "Point of sale",
                "BulkProductImport",
                "Serialized inventory",
                "DynamicPricingEngine",
                "Suppliers",
                "CustomerBehaviorAnalyticsPage",
                "FinancePostingRequests",
                "FinanceReconciliation",
                "HRMonthlyAttendance",
                "PayrollRuns",
                "MainBranchSettings",
                "OfflineSyncAdmin",
                "لوحة التحكم",
                "نقطة البيع",
                "المخزون",
                "مسير رواتب"
        )) {
            assertTrue(sql.contains(requiredCoverage), "Missing RAG coverage for " + requiredCoverage);
        }
    }
}
