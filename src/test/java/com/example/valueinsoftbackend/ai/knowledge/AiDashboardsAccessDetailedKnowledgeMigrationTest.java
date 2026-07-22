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

class AiDashboardsAccessDetailedKnowledgeMigrationTest {

    private static final String MIGRATION =
            "db/migration/V166__ai_dashboards_access_detailed_knowledge.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "\\('(?<document>42000000-0000-0000-0000-0000000000\\d{2})',\\s*'[^']+',\\s*'[^']+',\\s*(?<chunk>\\d+),"
    );

    @Test
    void seedsDetailedDashboardsAndAccessKnowledgeIntoBothRagStores() throws Exception {
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

        assertEquals(38, documentIds.size());
        assertEquals(114, chunks);
        assertTrue(sql.contains("INSERT INTO public.ai_document\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_document_chunk\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_knowledge_document\n"));
        assertTrue(sql.contains("INSERT INTO public.ai_knowledge_chunk\n"));
        assertTrue(sql.contains("rag-dashboard-access-detail-v1"));
        assertTrue(sql.contains("'keyword-fallback'"));
        assertTrue(sql.contains("embeddingStatus"));
    }

    @Test
    void coversImplementedDashboardAndAccessRulesAndBilingualTerms() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        for (String requiredCoverage : Set.of(
                "dashboard.home.view",
                "finance.report.read",
                "inventory.lowStockCriticalCount",
                "sectionStatus",
                "COMPANY_WEEKLY_PERFORMANCE",
                "company.insights.admin",
                "LOGIN_RATE_LIMITED",
                "requiredAllCapabilities",
                "package_locked",
                "BRANCH_ACCESS_DENIED",
                "EXPLICITLY_DENIED",
                "SELF_LOCKOUT_PROTECTED",
                "real model and grounded with retrieved RAG chunks",
                "لوحة المعلومات",
                "صلاحية",
                "نطاق الفرع"
        )) {
            assertTrue(sql.contains(requiredCoverage), "Missing detailed RAG coverage for " + requiredCoverage);
        }
    }
}
