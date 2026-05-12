package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.memory.AiConversationRecord;
import com.example.valueinsoftbackend.ai.memory.AiConversationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AiPermissionService {

    private static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN", "SUPPORTADMIN");
    private static final Pattern SECRET_LIKE_VALUE = Pattern.compile("(?i)(bearer\\s+)?[a-z0-9._\\-]{24,}");

    private final AiProperties aiProperties;
    private final AiSecurityContextResolver securityContextResolver;
    private final AiConversationRepository conversationRepository;
    private final AuthorizationService authorizationService;

    public AiPermissionService(AiProperties aiProperties,
                               AiSecurityContextResolver securityContextResolver,
                               AiConversationRepository conversationRepository,
                               AuthorizationService authorizationService) {
        this.aiProperties = aiProperties;
        this.securityContextResolver = securityContextResolver;
        this.conversationRepository = conversationRepository;
        this.authorizationService = authorizationService;
    }

    public void validateAiEnabled() {
        if (!aiProperties.isEnabled()) {
            throw new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_DISABLED",
                    "AI assistant is disabled"
            );
        }
    }

    public void validateModeAccess(AiSecurityContext context, AiMode mode) {
        if (mode == AiMode.HELP) {
            return;
        }

        if (!aiProperties.isToolsEnabled()) {
            throw new AiPermissionException("AI_TOOLS_DISABLED", "AI business tools are disabled");
        }

        if (mode.isAdmin()) {
            validateAdminAccess(context);
            return;
        }

        if (hasAnyCapability(context, null, capabilitiesFor(mode))) {
            return;
        }

        throw new AiPermissionException("AI_MODE_ACCESS_DENIED", "AI mode is not available");
    }

    public void validateBranchAccess(AiSecurityContext context, Long branchId) {
        securityContextResolver.validateBranchAccess(context, branchId);
    }

    public void validateBranchRequired(AiMode mode, Long branchId) {
        if (mode.requiresBranch() && branchId == null) {
            throw new AiPermissionException("AI_BRANCH_REQUIRED", "Branch is required for this AI mode");
        }
    }

    public AiConversationRecord validateConversationAccess(UUID conversationId, AiSecurityContext context) {
        AiConversationRecord conversation = conversationRepository.findActiveById(conversationId)
                .orElseThrow(() -> new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                        HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found"
                ));
        validateConversationAccess(conversation, context);
        return conversation;
    }

    public void validateConversationAccess(AiConversationRecord conversation, AiSecurityContext context) {
        if (conversation.companyId() != context.companyId() || conversation.userId() != context.userId()) {
            throw new AiPermissionException("CONVERSATION_ACCESS_DENIED", "Conversation access denied");
        }
        if (conversation.branchId() != null) {
            validateBranchAccess(context, conversation.branchId());
        }
    }

    public void validateToolAccess(String toolName, AiSecurityContext context, Long branchId) {
        validateBranchAccess(context, branchId);
        String normalizedTool = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        AiMode inferredMode = inferModeFromTool(normalizedTool);
        validateBranchRequired(inferredMode, branchId);
        validateModeAccess(context, inferredMode);
    }

    public void validateAdminAccess(AiSecurityContext context) {
        String role = context.role() == null ? "" : context.role().trim().toUpperCase(Locale.ROOT);
        if (ADMIN_ROLES.contains(role) || hasAnyCapability(context, null, List.of("platform.admin.read"))) {
            return;
        }
        throw new AiPermissionException("AI_ADMIN_ACCESS_DENIED", "Admin AI mode is not available");
    }

    public String maskSensitiveData(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return SECRET_LIKE_VALUE.matcher(value).replaceAll("[masked]");
    }

    private boolean hasAnyCapability(AiSecurityContext context, Long branchId, List<String> capabilities) {
        for (String capability : capabilities) {
            if (authorizationService.hasAuthenticatedCapability(
                    context.username(),
                    Math.toIntExact(context.companyId()),
                    branchId == null ? null : Math.toIntExact(branchId),
                    capability
            )) {
                return true;
            }
        }
        return false;
    }

    private List<String> capabilitiesFor(AiMode mode) {
        if (mode == AiMode.BUSINESS) {
            return List.of(
                    "dashboard.home.view",
                    "pos.sale.read",
                    "inventory.item.read",
                    "clients.account.read",
                    "suppliers.account.read",
                    "suppliers.list.view",
                    "pos.shift.read"
            );
        }
        if (mode == AiMode.SALES) {
            return List.of("pos.sale.read", "finance.report.read");
        }
        if (mode == AiMode.INVENTORY) {
            return List.of("inventory.item.read");
        }
        if (mode == AiMode.SUPPLIERS) {
            return List.of("suppliers.account.read", "suppliers.list.view", "suppliers.statement.view");
        }
        if (mode == AiMode.CUSTOMERS) {
            return List.of("clients.account.read");
        }
        if (mode == AiMode.SHIFT) {
            return List.of("pos.shift.read");
        }
        if (mode == AiMode.ADMIN) {
            return List.of("platform.admin.read");
        }
        return List.of();
    }

    private AiMode inferModeFromTool(String normalizedTool) {
        if ("inventory".equals(normalizedTool) || "inventoryaitools".equals(normalizedTool)) {
            return AiMode.INVENTORY;
        }
        if ("sales".equals(normalizedTool) || "salesaitools".equals(normalizedTool)) {
            return AiMode.SALES;
        }
        if ("shift".equals(normalizedTool) || "shiftaitools".equals(normalizedTool)) {
            return AiMode.SHIFT;
        }
        if ("supplier".equals(normalizedTool) || "supplieraitools".equals(normalizedTool)) {
            return AiMode.SUPPLIERS;
        }
        if ("customer".equals(normalizedTool) || "customeraitools".equals(normalizedTool)) {
            return AiMode.CUSTOMERS;
        }
        throw new AiPermissionException("AI_TOOL_ACCESS_DENIED", "AI tool is not available");
    }
}
