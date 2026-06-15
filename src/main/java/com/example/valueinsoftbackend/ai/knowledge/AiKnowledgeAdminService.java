package com.example.valueinsoftbackend.ai.knowledge;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentCreateRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentDto;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeDocumentListResponse;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeIngestResponse;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeIngestionJobDto;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeSearchTestRequest;
import com.example.valueinsoftbackend.ai.dto.AiKnowledgeSearchTestResponse;
import com.example.valueinsoftbackend.ai.dto.AiRetrievedChunkDto;
import com.example.valueinsoftbackend.ai.embedding.AiEmbeddingException;
import com.example.valueinsoftbackend.ai.service.AiPermissionService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.ai.service.AiSecurityContextResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AiKnowledgeAdminService {

    private static final Set<String> HELP_MODULES = Set.of(
            "general",
            "help",
            "manual",
            "faq",
            "inventory-help",
            "rental-help",
            "payment-help",
            "pos-help"
    );

    private final AiSecurityContextResolver securityContextResolver;
    private final AiPermissionService permissionService;
    private final PlatformAuthorizationService platformAuthorizationService;
    private final AiKnowledgeDocumentService documentService;
    private final AiKnowledgeDocumentRepository documentRepository;
    private final AiKnowledgeChunkRepository chunkRepository;
    private final AiKnowledgeIngestionService ingestionService;
    private final AiKnowledgeIngestionJobRepository ingestionJobRepository;
    private final AiRetrieverService retrieverService;
    private final DbBranch dbBranch;

    public AiKnowledgeAdminService(AiSecurityContextResolver securityContextResolver,
                                   AiPermissionService permissionService,
                                   PlatformAuthorizationService platformAuthorizationService,
                                   AiKnowledgeDocumentService documentService,
                                    AiKnowledgeDocumentRepository documentRepository,
                                    AiKnowledgeChunkRepository chunkRepository,
                                    AiKnowledgeIngestionService ingestionService,
                                    AiKnowledgeIngestionJobRepository ingestionJobRepository,
                                    AiRetrieverService retrieverService,
                                    DbBranch dbBranch) {
        this.securityContextResolver = securityContextResolver;
        this.permissionService = permissionService;
        this.platformAuthorizationService = platformAuthorizationService;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionService = ingestionService;
        this.ingestionJobRepository = ingestionJobRepository;
        this.retrieverService = retrieverService;
        this.dbBranch = dbBranch;
    }

    public AiKnowledgeIngestResponse createDocument(Principal principal, AiKnowledgeDocumentCreateRequest request) {
        AdminKnowledgeScope scope = effectiveScope(validateAdminScope(principal), request == null ? null : request.companyId());
        validateCreateRequest(request);
        validateBranchAccess(scope, request.branchId());

        AiKnowledgeDocumentRecord document = documentService.createDocument(new AiKnowledgeDocumentRecord(
                UUID.randomUUID(),
                scope.companyId(),
                tenantBranchId(scope, request.branchId()),
                normalizeModule(request.module()),
                defaultIfBlank(request.language(), "en").toLowerCase(Locale.ROOT),
                defaultIfBlank(request.documentType(), "HELP_ARTICLE"),
                request.title().trim(),
                defaultIfBlank(request.sourceType(), "MANUAL"),
                blankToNull(request.sourceUri()),
                null,
                request.content(),
                null,
                "DRAFT",
                "{}",
                scope.userId(),
                scope.userId(),
                Instant.now(),
                Instant.now()
        ));

        AiKnowledgeIngestionJobDto job = null;
        if (Boolean.TRUE.equals(request.ingestNow())) {
            job = ingestInternal(document.id(), scope);
            document = documentRepository.findById(document.id()).orElse(document);
        }
        return new AiKnowledgeIngestResponse(toDocumentDto(document), job);
    }

    public AiKnowledgeDocumentListResponse listDocuments(Principal principal,
                                                         String module,
                                                         String status,
                                                         Long companyId,
                                                         Long branchId,
                                                         String language,
                                                         Integer page,
                                                         Integer size) {
        AdminKnowledgeScope scope = effectiveScope(validateAdminScope(principal), companyId);
        validateBranchAccess(scope, branchId);
        int safePage = Math.max(0, page == null ? 0 : page);
        int safeSize = Math.max(1, Math.min(size == null ? 20 : size, 100));
        String normalizedModule = blankToNull(module) == null ? null : normalizeModule(module);
        String normalizedLanguage = blankToNull(language) == null ? null : language.trim().toLowerCase(Locale.ROOT);

        List<AiKnowledgeDocumentDto> items = documentRepository
                .listScoped(scope.companyId(), normalizedModule, blankToNull(status), tenantBranchId(scope, branchId), normalizedLanguage, safePage, safeSize)
                .stream()
                .map(this::toDocumentDto)
                .toList();
        long total = documentRepository.countScoped(scope.companyId(), normalizedModule, blankToNull(status), tenantBranchId(scope, branchId), normalizedLanguage);
        return new AiKnowledgeDocumentListResponse(items, safePage, safeSize, total);
    }

    public AiKnowledgeDocumentDto getDocument(Principal principal, UUID documentId) {
        AdminKnowledgeScope scope = validateAdminScope(principal);
        AiKnowledgeDocumentRecord document = requireDocumentInScope(documentId, scope);
        return toDocumentDto(document);
    }

    public AiKnowledgeIngestResponse ingestDocument(Principal principal, UUID documentId) {
        AdminKnowledgeScope scope = validateAdminScope(principal);
        requireDocumentInScope(documentId, scope);
        AiKnowledgeIngestionJobDto job = ingestInternal(documentId, scope);
        AiKnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .orElseThrow(() -> notFound("KNOWLEDGE_DOCUMENT_NOT_FOUND", "Knowledge document not found"));
        return new AiKnowledgeIngestResponse(toDocumentDto(document), job);
    }

    public AiKnowledgeIngestionJobDto getIngestionJob(Principal principal, UUID jobId) {
        AdminKnowledgeScope scope = validateAdminScope(principal);
        AiKnowledgeIngestionJobRecord job = ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> notFound("KNOWLEDGE_INGESTION_JOB_NOT_FOUND", "Knowledge ingestion job not found"));
        if (!platformAllCompanyScope(scope) && !sameScope(job.companyId(), scope.companyId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "KNOWLEDGE_INGESTION_JOB_NOT_FOUND", "Knowledge ingestion job not found");
        }
        validateBranchAccess(recordScope(scope, job.companyId()), job.branchId());
        return toJobDto(job);
    }

    public AiKnowledgeSearchTestResponse searchTest(Principal principal, AiKnowledgeSearchTestRequest request) {
        AdminKnowledgeScope scope = effectiveScope(validateAdminScope(principal), request == null ? null : request.companyId());
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw badRequest("KNOWLEDGE_SEARCH_QUERY_REQUIRED", "Search query is required");
        }
        validateBranchAccess(scope, request.branchId());
        Set<String> modules = requestedModules(request.module());
        List<AiRetrievedChunkDto> items = retrieverService.retrieve(new AiRetrievalRequest(
                        scope.companyId(),
                        scope.allowedBranchIds(),
                        tenantBranchId(scope, request.branchId()),
                        modules,
                        defaultIfBlank(request.language(), "en").toLowerCase(Locale.ROOT),
                        request.query().trim(),
                        request.topK(),
                        request.threshold(),
                        true,
                        true
                ))
                .stream()
                .map(this::toRetrievedChunkDto)
                .toList();
        return new AiKnowledgeSearchTestResponse(request.query().trim(), items);
    }

    private AiKnowledgeIngestionJobDto ingestInternal(UUID documentId, AdminKnowledgeScope scope) {
        try {
            AiKnowledgeIngestionJobRecord job = ingestionService.ingest(documentId);
            if (!sameScope(job.companyId(), scope.companyId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "KNOWLEDGE_DOCUMENT_NOT_FOUND", "Knowledge document not found");
            }
            return toJobDto(job);
        } catch (AiKnowledgeIngestionException exception) {
            throw mapIngestionFailure(exception);
        }
    }

    private AdminKnowledgeScope validateAdminScope(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        try {
            AiSecurityContext context = securityContextResolver.resolve(principal);
            // TODO: replace generic AI admin access with a dedicated ai.knowledge.manage capability.
            permissionService.validateAdminAccess(context);
            return new AdminKnowledgeScope(
                    context.companyId(),
                    context.userId(),
                    context.username(),
                    context.role(),
                    context.defaultBranchId(),
                    context.allowedBranchIds(),
                    true
            );
        } catch (ApiException exception) {
            if (!"TENANT_CONTEXT_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
            User platformUser = platformAuthorizationService.requirePlatformCapability(principal.getName(), "platform.admin.read");
            return new AdminKnowledgeScope(
                    null,
                    (long) platformUser.getUserId(),
                    platformUser.getUserName(),
                    platformUser.getRole(),
                    null,
                    Set.of(),
                    false
            );
        }
    }

    private AdminKnowledgeScope effectiveScope(AdminKnowledgeScope scope, Long requestedCompanyId) {
        if (requestedCompanyId == null) {
            return scope;
        }
        if (scope.tenantScoped()) {
            if (!requestedCompanyId.equals(scope.companyId())) {
                throw badRequest("KNOWLEDGE_COMPANY_SCOPE_FORBIDDEN", "Requested company scope does not match tenant context");
            }
            return scope;
        }
        if (requestedCompanyId <= 0) {
            throw badRequest("KNOWLEDGE_COMPANY_SCOPE_INVALID", "Company scope must be a positive id");
        }
        return new AdminKnowledgeScope(
                requestedCompanyId,
                scope.userId(),
                scope.username(),
                scope.role(),
                null,
                Set.of(),
                false
        );
    }

    private AiKnowledgeDocumentRecord requireDocumentInScope(UUID documentId, AdminKnowledgeScope scope) {
        if (documentId == null) {
            throw badRequest("KNOWLEDGE_DOCUMENT_ID_REQUIRED", "Knowledge document id is required");
        }
        AiKnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .orElseThrow(() -> notFound("KNOWLEDGE_DOCUMENT_NOT_FOUND", "Knowledge document not found"));
        if (!platformAllCompanyScope(scope) && !sameScope(document.companyId(), scope.companyId())) {
            throw notFound("KNOWLEDGE_DOCUMENT_NOT_FOUND", "Knowledge document not found");
        }
        validateBranchAccess(recordScope(scope, document.companyId()), document.branchId());
        return document;
    }

    private AdminKnowledgeScope recordScope(AdminKnowledgeScope actorScope, Long recordCompanyId) {
        if (!platformAllCompanyScope(actorScope)) {
            return actorScope;
        }
        if (recordCompanyId == null) {
            return actorScope;
        }
        return effectiveScope(actorScope, recordCompanyId);
    }

    private void validateBranchAccess(AdminKnowledgeScope scope, Long branchId) {
        if (branchId == null) {
            return;
        }
        if (!scope.tenantScoped()) {
            if (scope.companyId() == null) {
                throw badRequest("KNOWLEDGE_BRANCH_SCOPE_UNAVAILABLE", "Branch scope requires a company context");
            }
            Branch branch = dbBranch.getBranchById(Math.toIntExact(branchId));
            if ((long) branch.getBranchOfCompanyId() != scope.companyId()) {
                throw badRequest("KNOWLEDGE_BRANCH_SCOPE_INVALID", "Branch does not belong to the selected company");
            }
            return;
        }
        permissionService.validateBranchAccess(scope.toSecurityContext(), branchId);
    }

    private Long tenantBranchId(AdminKnowledgeScope scope, Long branchId) {
        return branchId;
    }

    private static boolean platformAllCompanyScope(AdminKnowledgeScope scope) {
        return scope != null && !scope.tenantScoped() && scope.companyId() == null;
    }

    private static boolean sameScope(Long recordCompanyId, Long scopeCompanyId) {
        return scopeCompanyId == null ? recordCompanyId == null : scopeCompanyId.equals(recordCompanyId);
    }

    private void validateCreateRequest(AiKnowledgeDocumentCreateRequest request) {
        if (request == null) {
            throw badRequest("KNOWLEDGE_DOCUMENT_REQUIRED", "Knowledge document request is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw badRequest("KNOWLEDGE_DOCUMENT_TITLE_REQUIRED", "Knowledge document title is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw badRequest("KNOWLEDGE_DOCUMENT_CONTENT_REQUIRED", "Knowledge document content is required");
        }
        normalizeModule(request.module());
    }

    private Set<String> requestedModules(String module) {
        if (module == null || module.isBlank()) {
            return HELP_MODULES;
        }
        return Set.of(normalizeModule(module));
    }

    private String normalizeModule(String module) {
        String normalized = module == null || module.isBlank()
                ? "help"
                : module.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw badRequest("KNOWLEDGE_MODULE_INVALID", "Knowledge module is invalid");
        }
        return normalized;
    }

    private AiKnowledgeDocumentDto toDocumentDto(AiKnowledgeDocumentRecord document) {
        return new AiKnowledgeDocumentDto(
                document.id(),
                document.companyId(),
                document.branchId(),
                document.module(),
                document.language(),
                document.documentType(),
                document.title(),
                document.sourceType(),
                document.sourceUri(),
                document.status(),
                preview(firstNonBlank(document.normalizedContent(), document.rawContent())),
                chunkRepository.countByDocumentId(document.id()),
                ingestionJobRepository.findLatestByDocumentId(document.id()).map(this::toJobDto).orElse(null),
                document.createdAt(),
                document.updatedAt()
        );
    }

    private AiKnowledgeIngestionJobDto toJobDto(AiKnowledgeIngestionJobRecord job) {
        return new AiKnowledgeIngestionJobDto(
                job.id(),
                job.documentId(),
                job.status(),
                job.chunkCount(),
                job.embeddingModel(),
                safeError(job.errorMessage()),
                job.startedAt(),
                job.finishedAt(),
                job.createdAt()
        );
    }

    private AiRetrievedChunkDto toRetrievedChunkDto(AiRetrievedChunk chunk) {
        return new AiRetrievedChunkDto(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.documentTitle(),
                chunk.module(),
                chunk.branchId(),
                chunk.language(),
                chunk.heading(),
                preview(firstNonBlank(chunk.contentPreview(), chunk.content())),
                chunk.similarity(),
                chunk.retrievalType()
        );
    }

    private ApiException mapIngestionFailure(AiKnowledgeIngestionException exception) {
        AiEmbeddingException embeddingException = findCause(exception, AiEmbeddingException.class);
        if (embeddingException != null) {
            if (embeddingException.getCategory() == AiEmbeddingException.Category.MISSING_API_KEY) {
                return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_EMBEDDING_API_KEY_MISSING",
                        "Embedding provider API key is not configured");
            }
            String message = embeddingException.getMessage() == null ? "" : embeddingException.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("disabled")) {
                return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_EMBEDDING_DISABLED",
                        "AI embeddings are disabled");
            }
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_EMBEDDING_UNAVAILABLE",
                    "Embedding provider is unavailable");
        }
        return new ApiException(HttpStatus.BAD_REQUEST,
                "KNOWLEDGE_INGESTION_FAILED",
                safeError(exception.getMessage()));
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private static String preview(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 260) {
            return normalized;
        }
        return normalized.substring(0, 257).trim() + "...";
    }

    private static String safeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("(?i)(api[_ -]?key|token|secret|password)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private record AdminKnowledgeScope(
            Long companyId,
            long userId,
            String username,
            String role,
            Long defaultBranchId,
            Set<Long> allowedBranchIds,
            boolean tenantScoped
    ) {
        AiSecurityContext toSecurityContext() {
            return new AiSecurityContext(
                    companyId == null ? 0L : companyId,
                    userId,
                    username,
                    role,
                    defaultBranchId,
                    allowedBranchIds == null ? Set.of() : allowedBranchIds
            );
        }
    }
}
