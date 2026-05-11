package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.audit.AiUsageLogService;
import com.example.valueinsoftbackend.ai.dto.AiConversationDto;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiChatResponse;
import com.example.valueinsoftbackend.ai.dto.AiMessageDto;
import com.example.valueinsoftbackend.ai.memory.AiConversationRecord;
import com.example.valueinsoftbackend.ai.memory.AiConversationRepository;
import com.example.valueinsoftbackend.ai.memory.AiMessageRecord;
import com.example.valueinsoftbackend.ai.memory.AiMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
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

    public AiChatService(AiPromptPolicyService promptPolicyService,
                         AiChatOrchestratorService orchestratorService,
                         AiSecurityContextResolver securityContextResolver,
                         AiPermissionService permissionService,
                         AiConversationRepository conversationRepository,
                         AiMessageRepository messageRepository,
                         AiUsageLogService usageLogService,
                         AiRateLimitService rateLimitService,
                         AiCostTrackingService costTrackingService) {
        this.promptPolicyService = promptPolicyService;
        this.orchestratorService = orchestratorService;
        this.securityContextResolver = securityContextResolver;
        this.permissionService = permissionService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.usageLogService = usageLogService;
        this.rateLimitService = rateLimitService;
        this.costTrackingService = costTrackingService;
    }

    public AiChatResponse chat(AiChatRequest request, Principal principal) {
        long startedAt = System.nanoTime();
        permissionService.validateAiEnabled();

        AiSecurityContext securityContext = securityContextResolver.resolve(principal);
        Long requestedBranchId = request.branchId();
        AiMode mode = AiMode.from(request.mode());
        permissionService.validateBranchRequired(mode, requestedBranchId);
        permissionService.validateBranchAccess(securityContext, requestedBranchId);
        permissionService.validateModeAccess(securityContext, mode);
        rateLimitService.validateDailyUserRequestLimit(securityContext);
        costTrackingService.validateCompanyMonthlyTokenLimit(securityContext);

        String normalizedMode = mode.name();
        AiConversationRecord conversation = resolveConversation(request, securityContext, normalizedMode, requestedBranchId);
        List<AiMessageRecord> recentMessages = messageRepository.findByConversation(conversation.id(), 12);
        String conversationContext = buildConversationContext(recentMessages);

        messageRepository.create(
                conversation.id(),
                securityContext.companyId(),
                conversation.branchId(),
                securityContext.userId(),
                "USER",
                request.message(),
                0
        );

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

        return new AiChatResponse(
                conversation.id().toString(),
                result.answer(),
                normalizedMode,
                result.suggestedQuestions(),
                result.actions(),
                result.sources(),
                result.toolCalls()
        );
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

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
