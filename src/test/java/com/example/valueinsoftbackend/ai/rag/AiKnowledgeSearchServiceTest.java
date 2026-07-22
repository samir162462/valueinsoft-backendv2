package com.example.valueinsoftbackend.ai.rag;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiKnowledgeSearchServiceTest {

    @Test
    void dashboardExperiencesFindsDashboardKnowledgeAndExcludesOtherModules() {
        AiDocumentChunkRepository repository = mock(AiDocumentChunkRepository.class);
        AiKnowledgeSearchService service = new AiKnowledgeSearchService(repository);
        AiDocumentChunkRecord dashboard = chunk(
                "Dashboard types navigation and purpose",
                "dashboard",
                "Available dashboards\nValueInSoft has several dashboard experiences."
        );
        AiDocumentChunkRecord inventory = chunk(
                "Inventory dashboard workspace",
                "inventory",
                "The Inventory workspace has a dashboard."
        );
        when(repository.findActiveChunks(1095L, "en", 500)).thenReturn(List.of(inventory, dashboard));

        List<AiKnowledgeSearchResult> results = service.search(
                1095L,
                "en",
                Set.of("dashboard", "help"),
                "dashboard experiences",
                5
        );

        assertEquals(1, results.size());
        assertEquals("Dashboard types navigation and purpose", results.get(0).chunk().title());
        verify(repository).findActiveChunks(1095L, "en", 500);
    }

    private AiDocumentChunkRecord chunk(String title, String module, String content) {
        return new AiDocumentChunkRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                title,
                module,
                "en",
                0,
                content,
                "{}",
                Instant.now()
        );
    }
}
