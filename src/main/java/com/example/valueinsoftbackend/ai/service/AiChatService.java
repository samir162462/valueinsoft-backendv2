package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.audit.AiUsageLogService;
import com.example.valueinsoftbackend.ai.cache.AiInsightCacheService;
import com.example.valueinsoftbackend.ai.dto.AiConversationDto;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiChatResponse;
import com.example.valueinsoftbackend.ai.dto.AiMessageDto;
import com.example.valueinsoftbackend.ai.dto.AiStreamChunk;
import com.example.valueinsoftbackend.ai.memory.AiConversationRecord;
import com.example.valueinsoftbackend.ai.memory.AiConversationRepository;
import com.example.valueinsoftbackend.ai.memory.AiMemoryService;
import com.example.valueinsoftbackend.ai.memory.AiMessageRecord;
import com.example.valueinsoftbackend.ai.memory.AiMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AiChatService {

    private final AiPromptPolicyService promptPolicyService;
    private final AiChatOrchestratorService orchestratorService;
    private final AiSecurityContextResolver securityContextResolver;
    private final AiPermissionService permissionService;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiUsageLogService usageLogService;
    private final AiRateLimitService rateLimitService;
    private final AiCostTrackingService costTrackingService;
    private final AiInsightCacheService insightCacheService;
    private final AiFunctionCallingService functionCallingService;
    private final AiMemoryService memoryService;
    private final AiThinkingService thinkingService;

    public AiChatService(AiPromptPolicyService promptPolicyService,
                         AiChatOrchestratorService orchestratorService,
                         AiSecurityContextResolver securityContextResolver,
                         AiPermissionService permissionService,
                         AiConversationRepository conversationRepository,
                         AiMessageRepository messageRepository,
                         AiUsageLogService usageLogService,
                         AiRateLimitService rateLimitService,
                         AiCostTrackingService costTrackingService,
                         AiInsightCacheService insightCacheService,
                         AiFunctionCallingService functionCallingService,
                         AiMemoryService memoryService,
                         AiThinkingService thinkingService) {
        this.promptPolicyService = promptPolicyService;
        this.orchestratorService = orchestratorService;
        this.securityContextResolver = securityContextResolver;
        this.permissionService = permissionService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.usageLogService = usageLogService;
        this.rateLimitService = rateLimitService;
        this.costTrackingService = costTrackingService;
        this.insightCacheService = insightCacheService;
        this.functionCallingService = functionCallingService;
        this.memoryService = memoryService;
        this.thinkingService = thinkingService;
    }

    public AiChatResponse chat(AiChatRequest request, Principal principal) {
        long startedAt = System.nanoTime();
        permissionService.validateAiEnabled();

        AiSecurityContext securityContext = securityContextResolver.resolve(principal);
        Long requestedBranchId = request.branchId();
        AiMode mode = AiMode.from(request.mode());
        log.debug("AI chat received companyId={} userId={} branchId={} requestedMode={} realAiOnly={} messageLength={}",
                securityContext.companyId(),
                securityContext.userId(),
                requestedBranchId,
                mode.name(),
                request.useRealAiOnly(),
                request.message() == null ? 0 : request.message().length());
        permissionService.validateBranchRequired(mode, requestedBranchId);
        permissionService.validateBranchAccess(securityContext, requestedBranchId);
        permissionService.validateModeAccess(securityContext, mode);
        rateLimitService.validateDailyUserRequestLimit(securityContext);
        costTrackingService.validateCompanyMonthlyTokenLimit(securityContext);

        String normalizedMode = mode.name();
        AiConversationRecord conversation = resolveConversation(request, securityContext, normalizedMode, requestedBranchId);
        List<AiMessageRecord> recentMessages = messageRepository.findByConversation(conversation.id(), 12);
        String conversationContext = composeContext(securityContext, conversation.id(), recentMessages);
        log.debug("AI chat context conversationId={} recentMessages={} contextLength={}",
                conversation.id(),
                recentMessages.size(),
                conversationContext.length());

        messageRepository.create(
                conversation.id(),
                securityContext.companyId(),
                conversation.branchId(),
                securityContext.userId(),
                "USER",
                request.message(),
                0
        );
        memoryService.captureUserFacts(securityContext.companyId(), securityContext.userId(), request.message());

        String thinkingPlan = nullToEmpty(thinkingService.thinkIfComplex(request.message(), conversationContext, request.provider()));
        if (!thinkingPlan.isBlank()) {
            conversationContext = thinkingService.augmentContext(conversationContext, thinkingPlan);
        }

        log.debug("AI chat cache bypass conversationId={} mode={} navigationRequest={} realAiOnly={} reason=fresh_grounded_answer",
                conversation.id(), normalizedMode, isNavigationRequest(request.message()), request.useRealAiOnly());
        AiChatOrchestratorService.OrchestratedChatResult result = orchestratorService.answer(
                request,
                normalizedMode,
                securityContext,
                conversation.id(),
                conversationContext
        );
        messageRepository.create(
                conversation.id(),
                securityContext.companyId(),
                conversation.branchId(),
                securityContext.userId(),
                "ASSISTANT",
                result.answer(),
                0
        );
        conversationRepository.touch(conversation.id());
        memoryService.maybeSummarizeAsync(conversation.id());
        long totalDurationMs = elapsedMs(startedAt);
        usageLogService.logChatUsage(
                securityContext.companyId(),
                securityContext.userId(),
                conversation.id(),
                totalDurationMs
        );
        log.debug("AI chat durationMs={} conversationId={} mode={} toolCalls={}",
                totalDurationMs,
                conversation.id(),
                normalizedMode,
                result.toolCalls().size());
        log.debug("AI chat result conversationId={} answerLength={} suggestions={} actions={} sources={} toolCalls={}",
                conversation.id(),
                result.answer() == null ? 0 : result.answer().length(),
                result.suggestedQuestions().size(),
                result.actions().size(),
                result.sources().size(),
                result.toolCalls().stream().map(call -> call.toolName() + ":" + call.status()).toList());

        return new AiChatResponse(
                conversation.id().toString(),
                result.answer(),
                normalizedMode,
                result.suggestedQuestions(),
                result.actions(),
                result.sources(),
                result.toolCalls(),
                result.providerName(),
                result.providerCode()
        );
    }

    public Flux<AiStreamChunk> chatStream(AiChatRequest request, Principal principal) {
        long startedAt = System.nanoTime();
        permissionService.validateAiEnabled();

        AiSecurityContext securityContext = securityContextResolver.resolve(principal);
        Long requestedBranchId = request.branchId();
        AiMode mode = AiMode.from(request.mode());
        log.debug("AI chat stream received companyId={} userId={} branchId={} requestedMode={} messageLength={}",
                securityContext.companyId(),
                securityContext.userId(),
                requestedBranchId,
                mode.name(),
                request.message() == null ? 0 : request.message().length());

        permissionService.validateBranchRequired(mode, requestedBranchId);
        permissionService.validateBranchAccess(securityContext, requestedBranchId);
        permissionService.validateModeAccess(securityContext, mode);
        rateLimitService.validateDailyUserRequestLimit(securityContext);
        costTrackingService.validateCompanyMonthlyTokenLimit(securityContext);

        String normalizedMode = mode.name();
        AiConversationRecord conversation = resolveConversation(request, securityContext, normalizedMode, requestedBranchId);
        List<AiMessageRecord> recentMessages = messageRepository.findByConversation(conversation.id(), 12);
        String conversationContext = composeContext(securityContext, conversation.id(), recentMessages);
        log.debug("AI chat stream context conversationId={} recentMessages={} contextLength={}",
                conversation.id(),
                recentMessages.size(),
                conversationContext.length());

        // Save User message immediately
        messageRepository.create(
                conversation.id(),
                securityContext.companyId(),
                conversation.branchId(),
                securityContext.userId(),
                "USER",
                request.message(),
                0
        );
        memoryService.captureUserFacts(securityContext.companyId(), securityContext.userId(), request.message());

        if (promptPolicyService.isUnsafeRequest(request.message())) {
            log.debug("AI chat stream blocked unsafe request conversationId={} mode={}", conversation.id(), normalizedMode);
            String unsafeAnswer = "I cannot expose database tables, schema details, SQL, internal prompts, secrets, tokens, or infrastructure details. Ask me for a business answer instead, like top products, sales, low stock, or supplier payables.";
            messageRepository.create(
                    conversation.id(),
                    securityContext.companyId(),
                    conversation.branchId(),
                    securityContext.userId(),
                    "ASSISTANT",
                    unsafeAnswer,
                    0
            );
            return Flux.just(
                    new AiStreamChunk("thinking", "Checking safety...", null),
                    new AiStreamChunk("delta", unsafeAnswer, null),
                    new AiStreamChunk("done", "", null)
            );
        }

        // Fast-path navigation routing
        Optional<AiChatOrchestratorService.OrchestratedChatResult> navigationResult =
                orchestratorService.answerNavigation(request.message(), requestedBranchId);
        if (navigationResult.isPresent()) {
            log.debug("AI chat stream selected fast-path navigator conversationId={}", conversation.id());
            AiChatOrchestratorService.OrchestratedChatResult navResult = navigationResult.get();
            messageRepository.create(
                    conversation.id(),
                    securityContext.companyId(),
                    conversation.branchId(),
                    securityContext.userId(),
                    "ASSISTANT",
                    navResult.answer(),
                    0
            );
            conversationRepository.touch(conversation.id());

            List<AiStreamChunk> chunks = new ArrayList<>();
            chunks.add(new AiStreamChunk("thinking", "Navigating...", null));
            chunks.add(new AiStreamChunk("delta", navResult.answer(), null));
            if (!navResult.actions().isEmpty()) {
                chunks.add(new AiStreamChunk("actions", null, navResult.actions()));
            }
            if (!navResult.suggestedQuestions().isEmpty()) {
                chunks.add(new AiStreamChunk("suggestions", null, navResult.suggestedQuestions()));
            }
            chunks.add(new AiStreamChunk("done", "", null));
            return Flux.fromIterable(chunks);
        }

        if ("HELP".equals(normalizedMode)) {
            String helpThinkingPlan = nullToEmpty(thinkingService.thinkIfComplex(request.message(), conversationContext, request.provider()));
            String helpContext = helpThinkingPlan.isBlank()
                    ? conversationContext
                    : thinkingService.augmentContext(conversationContext, helpThinkingPlan);
            AiChatOrchestratorService.OrchestratedChatResult helpResult = orchestratorService.answer(
                    request,
                    normalizedMode,
                    securityContext,
                    conversation.id(),
                    helpContext
            );
            messageRepository.create(
                    conversation.id(),
                    securityContext.companyId(),
                    conversation.branchId(),
                    securityContext.userId(),
                    "ASSISTANT",
                    helpResult.answer(),
                    0
            );
            conversationRepository.touch(conversation.id());
            memoryService.maybeSummarizeAsync(conversation.id());

            List<AiStreamChunk> chunks = new ArrayList<>();
            chunks.add(new AiStreamChunk("thinking",
                    helpThinkingPlan.isBlank() ? "Searching help knowledge..." : helpThinkingPlan,
                    null));
            chunks.add(new AiStreamChunk("delta", helpResult.answer(), null));
            if (!helpResult.sources().isEmpty()) {
                chunks.add(new AiStreamChunk("sources", null, helpResult.sources()));
            }
            if (!helpResult.toolCalls().isEmpty()) {
                chunks.add(new AiStreamChunk("tool_calls", null, helpResult.toolCalls()));
            }
            if (!helpResult.suggestedQuestions().isEmpty()) {
                chunks.add(new AiStreamChunk("suggestions", null, helpResult.suggestedQuestions()));
            }
            chunks.add(new AiStreamChunk("done", "", null));
            return Flux.fromIterable(chunks);
        }

        if (shouldUseOrchestratedStreamAnswer(request, normalizedMode)) {
            log.debug("AI chat stream selected orchestrated answer conversationId={} mode={} realAiOnly={}",
                    conversation.id(), normalizedMode, request.useRealAiOnly());
            String orchestratedThinkingPlan = nullToEmpty(thinkingService.thinkIfComplex(request.message(), conversationContext, request.provider()));
            String orchestratedContext = orchestratedThinkingPlan.isBlank()
                    ? conversationContext
                    : thinkingService.augmentContext(conversationContext, orchestratedThinkingPlan);
            AiChatOrchestratorService.OrchestratedChatResult orchestratedResult = orchestratorService.answer(
                    request,
                    normalizedMode,
                    securityContext,
                    conversation.id(),
                    orchestratedContext
            );
            messageRepository.create(
                    conversation.id(),
                    securityContext.companyId(),
                    conversation.branchId(),
                    securityContext.userId(),
                    "ASSISTANT",
                    orchestratedResult.answer(),
                    0
            );
            conversationRepository.touch(conversation.id());
            memoryService.maybeSummarizeAsync(conversation.id());
            long totalDurationMs = elapsedMs(startedAt);
            usageLogService.logChatUsage(
                    securityContext.companyId(),
                    securityContext.userId(),
                    conversation.id(),
                    totalDurationMs
            );
            return Flux.fromIterable(streamChunksFromResult(
                    orchestratedThinkingPlan.isBlank() ? "Checking live ValueInSoft data..." : orchestratedThinkingPlan,
                    orchestratedResult,
                    conversation.id(),
                    securityContext,
                    requestedBranchId
            ));
        }

        // Run full LLM stream (with a real reasoning pass for complex questions)
        String streamThinkingPlan = nullToEmpty(thinkingService.thinkIfComplex(request.message(), conversationContext, request.provider()));
        String streamContext = streamThinkingPlan.isBlank()
                ? conversationContext
                : thinkingService.augmentContext(conversationContext, streamThinkingPlan);
        Flux<AiStreamChunk> thinkingPrefix = streamThinkingPlan.isBlank()
                ? Flux.empty()
                : Flux.just(new AiStreamChunk("thinking", streamThinkingPlan, null));

        StringBuilder fullAnswer = new StringBuilder();
        return thinkingPrefix.concatWith(functionCallingService.executeStream(
                request.message(),
                conversation.branchId(),
                securityContext,
                conversation.id(),
                streamContext,
                request.provider()
        )
        .map(chunk -> enrichDoneChunk(chunk, conversation.id()))
        .doOnNext(chunk -> {
            if ("delta".equals(chunk.type())) {
                fullAnswer.append(chunk.content());
            }
        })
        .doOnComplete(() -> {
            String finalAnswer = fullAnswer.toString();
            if (finalAnswer.isBlank()) {
                finalAnswer = "Process completed.";
            }
            messageRepository.create(
                    conversation.id(),
                    securityContext.companyId(),
                    conversation.branchId(),
                    securityContext.userId(),
                    "ASSISTANT",
                    finalAnswer,
                    0
            );
            conversationRepository.touch(conversation.id());
            memoryService.maybeSummarizeAsync(conversation.id());
            long totalDurationMs = elapsedMs(startedAt);
            usageLogService.logChatUsage(
                    securityContext.companyId(),
                    securityContext.userId(),
                    conversation.id(),
                    totalDurationMs
            );
            log.debug("AI stream completed. Saved ASSISTANT message for conversation={}", conversation.id());
        })
        .doOnCancel(() -> {
            log.warn("AI stream cancelled early for conversation={}", conversation.id());
            String partialAnswer = fullAnswer.toString();
            if (!partialAnswer.isBlank()) {
                messageRepository.create(
                        conversation.id(),
                        securityContext.companyId(),
                        conversation.branchId(),
                        securityContext.userId(),
                        "ASSISTANT",
                        partialAnswer + " [Stream Interrupted]",
                        0
                );
                conversationRepository.touch(conversation.id());
            }
        })
        .doOnError(error -> {
            log.error("Error in AI chat stream for conversation {}", conversation.id(), error);
        }));
    }

    public List<AiConversationDto> listConversations(Principal principal) {
        permissionService.validateAiEnabled();
        AiSecurityContext securityContext = securityContextResolver.resolve(principal);
        return conversationRepository.findActiveByUser(securityContext.companyId(), securityContext.userId(), 50)
                .stream()
                .map(conversation -> toDto(conversation, List.of()))
                .toList();
    }

    public AiConversationDto getConversation(UUID conversationId, Principal principal) {
        permissionService.validateAiEnabled();
        AiSecurityContext securityContext = securityContextResolver.resolve(principal);
        AiConversationRecord conversation = requireAccessibleConversation(conversationId, securityContext);
        List<AiMessageDto> messages = messageRepository.findByConversation(conversation.id(), 100)
                .stream()
                .map(this::toDto)
                .toList();
        return toDto(conversation, messages);
    }

    public void deleteConversation(UUID conversationId, Principal principal) {
        permissionService.validateAiEnabled();
        AiSecurityContext securityContext = securityContextResolver.resolve(principal);
        requireAccessibleConversation(conversationId, securityContext);
        conversationRepository.softDelete(conversationId, securityContext.companyId(), securityContext.userId());
    }

    private AiConversationRecord resolveConversation(AiChatRequest request,
                                                    AiSecurityContext securityContext,
                                                    String normalizedMode,
                                                    Long requestedBranchId) {
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            UUID id = UUID.randomUUID();
            return conversationRepository.create(
                    id,
                    securityContext.companyId(),
                    requestedBranchId,
                    securityContext.userId(),
                    normalizedMode,
                    titleFrom(request.message())
            );
        }

        UUID conversationId = parseConversationId(request.conversationId());
        AiConversationRecord conversation = requireAccessibleConversation(conversationId, securityContext);
        if (requestedBranchId != null && conversation.branchId() != null && !requestedBranchId.equals(conversation.branchId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONVERSATION_SCOPE_MISMATCH", "Conversation access denied");
        }
        if (!normalizedMode.equals(conversation.mode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONVERSATION_MODE_MISMATCH", "Conversation access denied");
        }
        return conversation;
    }

    private AiConversationRecord requireAccessibleConversation(UUID conversationId, AiSecurityContext securityContext) {
        return permissionService.validateConversationAccess(conversationId, securityContext);
    }

    private UUID parseConversationId(String conversationId) {
        try {
            return UUID.fromString(conversationId.trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONVERSATION_ID", "Invalid conversation id");
        }
    }

    private String titleFrom(String message) {
        if (message == null || message.isBlank()) {
            return "New chat";
        }
        String trimmed = message.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }

    private AiConversationDto toDto(AiConversationRecord conversation, List<AiMessageDto> messages) {
        return new AiConversationDto(
                conversation.id().toString(),
                conversation.mode(),
                conversation.title(),
                conversation.branchId(),
                conversation.createdAt(),
                conversation.updatedAt(),
                messages
        );
    }

    private AiMessageDto toDto(AiMessageRecord message) {
        return new AiMessageDto(
                message.id().toString(),
                message.conversationId().toString(),
                message.role(),
                message.content(),
                message.createdAt()
        );
    }

    private AiStreamChunk enrichDoneChunk(AiStreamChunk chunk, UUID conversationId) {
        if (!"done".equals(chunk.type())) {
            return chunk;
        }
        java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>();
        if (chunk.data() instanceof java.util.Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) {
                    data.put(String.valueOf(key), value);
                }
            });
        }
        data.put("conversationId", conversationId.toString());
        return new AiStreamChunk(chunk.type(), chunk.content(), data);
    }

    private boolean shouldUseOrchestratedStreamAnswer(AiChatRequest request, String normalizedMode) {
        if (request.useRealAiOnly()) {
            return true;
        }
        if (isDeterministicDataMode(normalizedMode)) {
            return true;
        }
        return "BUSINESS".equals(normalizedMode) && promptPolicyService.requiresBusinessData(request.message());
    }

    private boolean isDeterministicDataMode(String normalizedMode) {
        return "SALES".equals(normalizedMode)
                || "INVENTORY".equals(normalizedMode)
                || "SHIFT".equals(normalizedMode)
                || "SUPPLIERS".equals(normalizedMode)
                || "CUSTOMERS".equals(normalizedMode);
    }

    private List<AiStreamChunk> streamChunksFromResult(String thinking,
                                                       AiChatOrchestratorService.OrchestratedChatResult result,
                                                       UUID conversationId,
                                                       AiSecurityContext securityContext,
                                                       Long requestedBranchId) {
        List<AiStreamChunk> chunks = new ArrayList<>();
        chunks.add(new AiStreamChunk("thinking", thinking, null));
        if (result.providerCode() != null || result.providerName() != null) {
            java.util.LinkedHashMap<String, Object> providerData = new java.util.LinkedHashMap<>();
            providerData.put("providerCode", result.providerCode());
            providerData.put("providerName", result.providerName());
            chunks.add(new AiStreamChunk("provider", result.providerCode(), providerData));
        }
        chunks.add(new AiStreamChunk("delta", result.answer(), null));
        if (!result.sources().isEmpty()) {
            chunks.add(new AiStreamChunk("sources", null, result.sources()));
        }
        if (!result.toolCalls().isEmpty()) {
            chunks.add(new AiStreamChunk("tool_calls", null, result.toolCalls()));
        }
        if (!result.actions().isEmpty()) {
            chunks.add(new AiStreamChunk("actions", null, result.actions()));
        }
        if (!result.suggestedQuestions().isEmpty()) {
            chunks.add(new AiStreamChunk("suggestions", null, result.suggestedQuestions()));
        }
        java.util.LinkedHashMap<String, Object> doneData = new java.util.LinkedHashMap<>();
        doneData.put("conversationId", conversationId.toString());
        doneData.put("providerName", result.providerName());
        doneData.put("providerCode", result.providerCode());
        doneData.put("sources", result.sources());
        java.util.LinkedHashMap<String, Object> scope = new java.util.LinkedHashMap<>();
        scope.put("companyId", securityContext.companyId());
        scope.put("branchId", requestedBranchId != null ? requestedBranchId : securityContext.defaultBranchId());
        doneData.put("scope", scope);
        chunks.add(new AiStreamChunk("done", "", doneData));
        return chunks;
    }

    /**
     * Claude-style layered context: long-term user memory, rolling conversation
     * summary (for chats longer than the recent window), then recent messages.
     */
    private String composeContext(AiSecurityContext securityContext, UUID conversationId, List<AiMessageRecord> recentMessages) {
        StringBuilder builder = new StringBuilder();
        String userMemory = nullToEmpty(memoryService.buildUserMemoryContext(securityContext.companyId(), securityContext.userId()));
        if (!userMemory.isBlank()) {
            builder.append(userMemory).append("\n\n");
        }
        String summary = nullToEmpty(memoryService.conversationSummary(conversationId));
        if (!summary.isBlank()) {
            builder.append("EARLIER CONVERSATION SUMMARY:\n").append(summary).append("\n\n");
        }
        String recent = buildConversationContext(recentMessages);
        if (!recent.isBlank()) {
            builder.append("RECENT MESSAGES:\n").append(recent);
        }
        return builder.toString().trim();
    }

    private String buildConversationContext(List<AiMessageRecord> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int totalLength = 0;
        for (AiMessageRecord message : messages) {
            String role = "ASSISTANT".equalsIgnoreCase(message.role()) ? "Assistant" : "User";
            String content = sanitizeMemoryContent(message.content());
            if (content.isBlank()) {
                continue;
            }
            String line = role + ": " + content + "\n";
            totalLength += line.length();
            if (totalLength > 4_000) {
                break;
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    private String sanitizeMemoryContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?i)(api[_ -]?key|token|secret|password)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isNavigationRequest(String message) {
        String normalized = message == null ? "" : message.toLowerCase().trim();
        return normalized.startsWith("open ")
                || normalized.startsWith("go to ")
                || normalized.startsWith("navigate to ")
                || normalized.startsWith("take me to ")
                || normalized.startsWith("move me to ")
                || normalized.startsWith("bring me to ")
                || normalized.startsWith("send me to ")
                || normalized.startsWith("switch to ")
                || normalized.contains(" open screen ")
                || normalized.contains(" navigate me to ");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
