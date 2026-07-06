package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.audit.AiUsageLogService;
import com.example.valueinsoftbackend.ai.cache.AiInsightCacheService;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiChatResponse;
import com.example.valueinsoftbackend.ai.dto.AiSourceDto;
import com.example.valueinsoftbackend.ai.dto.AiStreamChunk;
import com.example.valueinsoftbackend.ai.dto.AiToolCallDto;
import com.example.valueinsoftbackend.ai.memory.AiConversationRecord;
import com.example.valueinsoftbackend.ai.memory.AiConversationRepository;
import com.example.valueinsoftbackend.ai.memory.AiMemoryService;
import com.example.valueinsoftbackend.ai.memory.AiMessageRecord;
import com.example.valueinsoftbackend.ai.memory.AiMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceStreamTest {

    private AiPromptPolicyService promptPolicyService;
    private AiChatOrchestratorService orchestratorService;
    private AiSecurityContextResolver securityContextResolver;
    private AiPermissionService permissionService;
    private AiConversationRepository conversationRepository;
    private AiMessageRepository messageRepository;
    private AiUsageLogService usageLogService;
    private AiRateLimitService rateLimitService;
    private AiCostTrackingService costTrackingService;
    private AiInsightCacheService insightCacheService;
    private AiFunctionCallingService functionCallingService;
    private AiMemoryService memoryService;
    private AiThinkingService thinkingService;
    private AiSecurityContext securityContext;
    private AiChatService chatService;

    @BeforeEach
    void setUp() {
        promptPolicyService = mock(AiPromptPolicyService.class);
        orchestratorService = mock(AiChatOrchestratorService.class);
        securityContextResolver = mock(AiSecurityContextResolver.class);
        permissionService = mock(AiPermissionService.class);
        conversationRepository = mock(AiConversationRepository.class);
        messageRepository = mock(AiMessageRepository.class);
        usageLogService = mock(AiUsageLogService.class);
        rateLimitService = mock(AiRateLimitService.class);
        costTrackingService = mock(AiCostTrackingService.class);
        insightCacheService = mock(AiInsightCacheService.class);
        functionCallingService = mock(AiFunctionCallingService.class);
        memoryService = mock(AiMemoryService.class);
        thinkingService = mock(AiThinkingService.class);
        when(memoryService.buildUserMemoryContext(anyLong(), anyLong())).thenReturn("");
        when(memoryService.conversationSummary(any(UUID.class))).thenReturn("");
        when(thinkingService.thinkIfComplex(any(), any(), any())).thenReturn("");
        securityContext = new AiSecurityContext(
                42L,
                7L,
                "sam",
                "OWNER",
                100L,
                Set.of(100L, 101L)
        );

        when(securityContextResolver.resolve(any())).thenReturn(securityContext);
        when(conversationRepository.create(any(UUID.class), eq(42L), eq(100L), eq(7L), anyString(), anyString()))
                .thenAnswer(invocation -> new AiConversationRecord(
                        invocation.getArgument(0),
                        42L,
                        100L,
                        7L,
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        Instant.now(),
                        Instant.now(),
                        false
                ));
        when(messageRepository.findByConversation(any(UUID.class), eq(12))).thenReturn(List.of());
        when(messageRepository.create(any(UUID.class), anyLong(), any(), anyLong(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> new AiMessageRecord(
                        UUID.randomUUID(),
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        Instant.now()
                ));

        chatService = new AiChatService(
                promptPolicyService,
                orchestratorService,
                securityContextResolver,
                permissionService,
                conversationRepository,
                messageRepository,
                usageLogService,
                rateLimitService,
                costTrackingService,
                insightCacheService,
                functionCallingService,
                memoryService,
                thinkingService
        );
    }

    @Test
    void streamInventoryModeUsesOrchestratorAndEmitsEvidenceChunks() {
        when(orchestratorService.answer(any(), eq("INVENTORY"), eq(securityContext), any(UUID.class), eq("")))
                .thenReturn(new AiChatOrchestratorService.OrchestratedChatResult(
                        "Low stock products:\n- iPhone 15",
                        List.of("Show low stock products"),
                        List.of(),
                        List.of(new AiSourceDto("getLowStockProducts", "TOOL", "Returned 1 row(s)")),
                        List.of(new AiToolCallDto("getLowStockProducts", "SUCCESS", "Returned 1 row(s)")),
                        "Gemini",
                        "GEMINI"
                ));

        Principal principal = () -> "sam";
        List<AiStreamChunk> chunks = chatService.chatStream(
                new AiChatRequest(null, "INVENTORY", "Show low stock products", 100L, false, "gemini"),
                principal
        ).collectList().block();

        assertFalse(chunks == null || chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(chunk -> "sources".equals(chunk.type())));
        assertTrue(chunks.stream().anyMatch(chunk -> "tool_calls".equals(chunk.type())));
        assertTrue(chunks.stream().anyMatch(chunk -> "delta".equals(chunk.type())
                && chunk.content().contains("Low stock products")));

        AiStreamChunk done = chunks.stream()
                .filter(chunk -> "done".equals(chunk.type()))
                .findFirst()
                .orElseThrow();
        Map<?, ?> doneData = (Map<?, ?>) done.data();
        assertTrue(doneData.containsKey("conversationId"));
        assertEquals("GEMINI", doneData.get("providerCode"));

        verify(orchestratorService).answer(any(), eq("INVENTORY"), eq(securityContext), any(UUID.class), eq(""));
        verify(functionCallingService, never()).executeStream(anyString(), any(), any(), any(), anyString(), any());
    }

    @Test
    void chatBusinessDataBypassesStaleInsightCacheForFreshGroundedAnswer() {
        when(promptPolicyService.requiresBusinessData(anyString())).thenReturn(true);
        when(insightCacheService.get(any(), any(), anyString(), anyString())).thenReturn(Optional.of("stale cached sales"));
        when(orchestratorService.answer(any(), eq("SALES"), eq(securityContext), any(UUID.class), eq("")))
                .thenReturn(new AiChatOrchestratorService.OrchestratedChatResult(
                        "Fresh live sales summary.",
                        List.of("Show payment breakdown"),
                        List.of(),
                        List.of(new AiSourceDto("getSalesSummaryByDateRange", "TOOL", "Returned 1 row(s)")),
                        List.of(new AiToolCallDto("getSalesSummaryByDateRange", "SUCCESS", "Returned 1 row(s)"))
                ));

        AiChatResponse response = chatService.chat(
                new AiChatRequest(null, "SALES", "What are today's sales?", 100L, false, "gemini"),
                () -> "sam"
        );

        assertEquals("Fresh live sales summary.", response.answer());
        assertFalse(response.sources().isEmpty());
        assertEquals("TOOL", response.sources().get(0).type());
        verify(insightCacheService, never()).get(any(), any(), anyString(), anyString());
        verify(insightCacheService, never()).put(any(), any(), anyString(), anyString(), anyString());
        verify(orchestratorService).answer(any(), eq("SALES"), eq(securityContext), any(UUID.class), eq(""));
    }
}
