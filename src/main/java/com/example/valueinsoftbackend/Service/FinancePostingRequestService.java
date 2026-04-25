package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinancePostingRequest;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestProcessResponse;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FinancePostingRequestService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_PAYLOAD_LENGTH = 200_000;
    private static final Set<String> SOURCE_MODULES = Set.of("pos", "purchase", "inventory", "payment", "expense");
    private static final Set<String> REQUEST_STATUSES = Set.of("pending", "processing", "posted", "failed", "ignored", "cancelled");
    private static final Pattern SOURCE_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]*$");

    private final DbFinancePostingRequest dbFinancePostingRequest;
    private final DbFinanceSetup dbFinanceSetup;
    private final DbFinanceJournal dbFinanceJournal;
    private final AuthorizationService authorizationService;
    private final FinanceAuditService financeAuditService;
    private final ObjectMapper objectMapper;
    private final List<FinancePostingAdapter> postingAdapters;

    public FinancePostingRequestService(DbFinancePostingRequest dbFinancePostingRequest,
                                        DbFinanceSetup dbFinanceSetup,
                                        DbFinanceJournal dbFinanceJournal,
                                        AuthorizationService authorizationService,
                                        FinanceAuditService financeAuditService,
                                        ObjectMapper objectMapper,
                                        List<FinancePostingAdapter> postingAdapters) {
        this.dbFinancePostingRequest = dbFinancePostingRequest;
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbFinanceJournal = dbFinanceJournal;
        this.authorizationService = authorizationService;
        this.financeAuditService = financeAuditService;
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.postingAdapters = postingAdapters;
    }

    @Transactional
    public FinancePostingRequestItem createPostingRequestForAuthenticatedUser(String authenticatedName,
                                                                              FinancePostingRequestCreateRequest request) {
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        authorizeCreate(authenticatedName, request.getCompanyId(), request.getBranchId());
        return createPostingRequest(authenticatedName, request);
    }

    @Transactional
    public FinancePostingRequestItem createPostingRequestFromSystem(String actorName,
                                                                    FinancePostingRequestCreateRequest request) {
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        return createPostingRequest(actorName, request);
    }

    private FinancePostingRequestItem createPostingRequest(String actorName,
                                                           FinancePostingRequestCreateRequest request) {
        normalizeAndValidateCreateRequest(request);

        String payloadJson = serializePayload(request.getRequestPayload());
        String requestHash = hashCanonicalRequest(request, payloadJson);
        FinancePostingRequestItem existing = dbFinancePostingRequest.findPostingRequestBySource(
                request.getCompanyId(),
                request.getSourceModule(),
                request.getSourceType(),
                request.getSourceId());

        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new ApiException(HttpStatus.CONFLICT, "FINANCE_POSTING_REQUEST_HASH_CONFLICT",
                        "A posting request already exists for this source with different normalized content");
            }
            return existing;
        }

        Integer actorUserId = financeAuditService.resolveActorUserId(actorName);
        try {
            FinancePostingRequestItem created = dbFinancePostingRequest.createPostingRequest(
                    request,
                    requestHash,
                    payloadJson,
                    actorUserId);
            financeAuditService.recordEvent(
                    actorName,
                    request.getCompanyId(),
                    request.getBranchId(),
                    "finance.posting_request.created",
                    "finance_posting_request",
                    created.getPostingRequestId().toString(),
                    Map.of(
                            "sourceModule", created.getSourceModule(),
                            "sourceType", created.getSourceType(),
                            "sourceId", created.getSourceId(),
                            "postingDate", created.getPostingDate().toString(),
                            "fiscalPeriodId", created.getFiscalPeriodId().toString(),
                            "status", created.getStatus(),
                            "requestHash", created.getRequestHash()),
                    "Finance posting request created");
            return created;
        } catch (DuplicateKeyException exception) {
            FinancePostingRequestItem duplicate = dbFinancePostingRequest.findPostingRequestBySource(
                    request.getCompanyId(),
                    request.getSourceModule(),
                    request.getSourceType(),
                    request.getSourceId());
            if (duplicate != null && requestHash.equals(duplicate.getRequestHash())) {
                return duplicate;
            }
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_POSTING_REQUEST_HASH_CONFLICT",
                    "A posting request already exists for this source with different normalized content");
        }
    }

    public ArrayList<FinancePostingRequestItem> getPostingRequestsForAuthenticatedUser(String authenticatedName,
                                                                                       int companyId,
                                                                                       Integer branchId,
                                                                                       String status,
                                                                                       String sourceModule,
                                                                                       UUID fiscalPeriodId,
                                                                                       Integer limit,
                                                                                       Integer offset) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId, branchId);

        String normalizedStatus = normalizeOptional(status);
        String normalizedSourceModule = normalizeOptional(sourceModule);
        validateOptionalStatus(normalizedStatus);
        validateOptionalSourceModule(normalizedSourceModule);
        if (fiscalPeriodId != null && !dbFinanceSetup.fiscalPeriodExists(companyId, fiscalPeriodId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }

        return dbFinancePostingRequest.getPostingRequests(
                companyId,
                branchId,
                normalizedStatus,
                normalizedSourceModule,
                fiscalPeriodId,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public FinancePostingRequestItem getPostingRequestForAuthenticatedUser(String authenticatedName,
                                                                           int companyId,
                                                                           UUID postingRequestId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId, null);
        return requirePostingRequest(companyId, postingRequestId);
    }

    @Transactional
    public FinancePostingRequestItem cancelPostingRequestForAuthenticatedUser(String authenticatedName,
                                                                              int companyId,
                                                                              UUID postingRequestId) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        FinancePostingRequestItem existing = requirePostingRequest(companyId, postingRequestId);
        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);

        int rows = dbFinancePostingRequest.cancelPendingPostingRequest(companyId, postingRequestId, actorUserId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_POSTING_REQUEST_NOT_CANCELLABLE",
                    "Only pending or failed posting requests can be cancelled");
        }

        FinancePostingRequestItem cancelled = dbFinancePostingRequest.getPostingRequestById(companyId, postingRequestId);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                cancelled.getBranchId(),
                "finance.posting_request.cancelled",
                "finance_posting_request",
                postingRequestId.toString(),
                Map.of(
                        "previousStatus", existing.getStatus(),
                        "status", cancelled.getStatus(),
                        "sourceModule", cancelled.getSourceModule(),
                        "sourceType", cancelled.getSourceType(),
                        "sourceId", cancelled.getSourceId()),
                "Finance posting request cancelled");
        return cancelled;
    }

    @Transactional
    public FinancePostingRequestItem retryPostingRequestForAuthenticatedUser(String authenticatedName,
                                                                             int companyId,
                                                                             UUID postingRequestId) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        FinancePostingRequestItem existing = requirePostingRequest(companyId, postingRequestId);
        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);

        int rows = dbFinancePostingRequest.retryFailedPostingRequest(companyId, postingRequestId, actorUserId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_POSTING_REQUEST_NOT_RETRYABLE",
                    "Only failed posting requests can be returned to pending");
        }

        FinancePostingRequestItem retried = dbFinancePostingRequest.getPostingRequestById(companyId, postingRequestId);
        financeAuditService.recordEvent(
                authenticatedName,
                companyId,
                retried.getBranchId(),
                "finance.posting_request.retry_requested",
                "finance_posting_request",
                postingRequestId.toString(),
                Map.of(
                        "previousStatus", existing.getStatus(),
                        "status", retried.getStatus(),
                        "sourceModule", retried.getSourceModule(),
                        "sourceType", retried.getSourceType(),
                        "sourceId", retried.getSourceId(),
                        "attemptCount", retried.getAttemptCount()),
                "Finance posting request retry requested");
        return retried;
    }

    @Transactional
    public FinancePostingRequestProcessResponse processPostingRequestForAuthenticatedUser(String authenticatedName,
                                                                                          int companyId,
                                                                                          UUID postingRequestId) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);

        FinancePostingRequestItem claimed = dbFinancePostingRequest.claimPendingPostingRequest(
                companyId,
                postingRequestId,
                actorUserId);
        if (claimed == null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_POSTING_REQUEST_NOT_PROCESSABLE",
                    "Only pending posting requests can be processed");
        }

        return processClaimedRequest(authenticatedName, claimed, actorUserId);
    }

    @Transactional
    public FinancePostingRequestProcessResponse processNextPostingRequestForAuthenticatedUser(String authenticatedName,
                                                                                              int companyId,
                                                                                              String sourceModule) {
        requireCompany(companyId);
        authorizeEdit(authenticatedName, companyId, null);
        String normalizedSourceModule = normalizeOptional(sourceModule);
        validateOptionalSourceModule(normalizedSourceModule);
        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);

        FinancePostingRequestItem claimed = dbFinancePostingRequest.claimNextPendingPostingRequest(
                companyId,
                normalizedSourceModule,
                actorUserId);
        if (claimed == null) {
            return new FinancePostingRequestProcessResponse(
                    companyId,
                    null,
                    normalizedSourceModule,
                    null,
                    null,
                    "none",
                    false,
                    null,
                    0,
                    "No pending posting request was available",
                    null);
        }

        return processClaimedRequest(authenticatedName, claimed, actorUserId);
    }

    private FinancePostingRequestProcessResponse processClaimedRequest(String authenticatedName,
                                                                       FinancePostingRequestItem claimed,
                                                                       Integer actorUserId) {
        try {
            FinancePostingAdapter adapter = resolveAdapter(claimed.getSourceModule());
            UUID journalEntryId = adapter.post(claimed);
            if (journalEntryId == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FINANCE_POSTING_ADAPTER_NO_JOURNAL",
                        "Posting adapter did not return a journal id");
            }

            FinancePostingRequestItem posted = dbFinancePostingRequest.markPostingRequestPosted(
                    claimed.getCompanyId(),
                    claimed.getPostingRequestId(),
                    journalEntryId,
                    actorUserId);
            String correlationId = recordProcessAudit(
                    authenticatedName,
                    posted,
                    "finance.posting_request.posted",
                    "Posting request processed");
            return processResponse(posted, true, "Posting request processed", correlationId);
        } catch (RuntimeException exception) {
            FinancePostingRequestItem failed = dbFinancePostingRequest.markPostingRequestFailed(
                    claimed.getCompanyId(),
                    claimed.getPostingRequestId(),
                    exception.getMessage(),
                    actorUserId);
            String correlationId = recordProcessAudit(
                    authenticatedName,
                    failed,
                    "finance.posting_request.failed",
                    "Posting request processing failed");
            return processResponse(failed, true, exception.getMessage(), correlationId);
        }
    }

    private FinancePostingAdapter resolveAdapter(String sourceModule) {
        for (FinancePostingAdapter adapter : postingAdapters) {
            if (adapter.supports(sourceModule)) {
                return adapter;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_ADAPTER_NOT_FOUND",
                "No finance posting adapter exists for source module");
    }

    private String recordProcessAudit(String authenticatedName,
                                      FinancePostingRequestItem request,
                                      String eventType,
                                      String reason) {
        return financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                eventType,
                "finance_posting_request",
                request.getPostingRequestId().toString(),
                Map.of(
                        "sourceModule", request.getSourceModule(),
                        "sourceType", request.getSourceType(),
                        "sourceId", request.getSourceId(),
                        "status", request.getStatus(),
                        "attemptCount", request.getAttemptCount(),
                        "journalEntryId", request.getJournalEntryId() == null ? "" : request.getJournalEntryId().toString(),
                        "lastError", request.getLastError() == null ? "" : request.getLastError()),
                reason);
    }

    private FinancePostingRequestProcessResponse processResponse(FinancePostingRequestItem request,
                                                                 boolean processed,
                                                                 String message,
                                                                 String correlationId) {
        return new FinancePostingRequestProcessResponse(
                request.getCompanyId(),
                request.getPostingRequestId(),
                request.getSourceModule(),
                request.getSourceType(),
                request.getSourceId(),
                request.getStatus(),
                processed,
                request.getJournalEntryId(),
                request.getAttemptCount(),
                message,
                correlationId);
    }

    private void normalizeAndValidateCreateRequest(FinancePostingRequestCreateRequest request) {
        request.setSourceModule(normalizeRequired(request.getSourceModule(), "FINANCE_SOURCE_MODULE_REQUIRED"));
        request.setSourceType(normalizeRequired(request.getSourceType(), "FINANCE_SOURCE_TYPE_REQUIRED"));
        request.setSourceId(normalizeRequired(request.getSourceId(), "FINANCE_SOURCE_ID_REQUIRED"));
        request.setRequestPayload(request.getRequestPayload() == null ? Map.of() : request.getRequestPayload());

        validateOptionalSourceModule(request.getSourceModule());
        if (!SOURCE_TYPE_PATTERN.matcher(request.getSourceType()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_SOURCE_TYPE_INVALID",
                    "Source type must use lowercase letters, numbers, dots, dashes, or underscores");
        }
        if (request.getSourceId().length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_SOURCE_ID_INVALID",
                    "Source id must be 128 characters or fewer");
        }
        if (request.getFiscalPeriodId() == null || !dbFinanceSetup.fiscalPeriodExists(request.getCompanyId(), request.getFiscalPeriodId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_FISCAL_PERIOD_INVALID",
                    "Fiscal period does not belong to the company");
        }

        DbFinanceJournal.FiscalPeriodPostingInfo period = dbFinanceJournal.getFiscalPeriodPostingInfo(
                request.getCompanyId(),
                request.getFiscalPeriodId());
        if (request.getPostingDate() == null
                || request.getPostingDate().isBefore(period.startDate())
                || request.getPostingDate().isAfter(period.endDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_DATE_OUTSIDE_PERIOD",
                    "Posting date must be inside the selected fiscal period");
        }
        if (!Set.of("open", "soft_locked").contains(period.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_PERIOD_NOT_OPEN",
                    "Posting requests can only target open or soft-locked periods");
        }
    }

    private FinancePostingRequestItem requirePostingRequest(int companyId, UUID postingRequestId) {
        try {
            return dbFinancePostingRequest.getPostingRequestById(companyId, postingRequestId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_POSTING_REQUEST_NOT_FOUND",
                    "Posting request does not exist for the company");
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.length() > MAX_PAYLOAD_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_REQUEST_PAYLOAD_TOO_LARGE",
                        "Posting request payload is too large");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_REQUEST_PAYLOAD_INVALID",
                    "Posting request payload must be JSON serializable");
        }
    }

    private String hashCanonicalRequest(FinancePostingRequestCreateRequest request, String payloadJson) {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("companyId", request.getCompanyId());
        canonical.put("branchId", request.getBranchId());
        canonical.put("sourceModule", request.getSourceModule());
        canonical.put("sourceType", request.getSourceType());
        canonical.put("sourceId", request.getSourceId());
        canonical.put("postingDate", request.getPostingDate().toString());
        canonical.put("fiscalPeriodId", request.getFiscalPeriodId().toString());
        canonical.put("payload", payloadJson);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FINANCE_POSTING_REQUEST_HASH_FAILED",
                    "Unable to calculate posting request hash");
        }
    }

    private String normalizeRequired(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Required posting request field is missing");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validateOptionalSourceModule(String sourceModule) {
        if (sourceModule != null && !SOURCE_MODULES.contains(sourceModule)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_SOURCE_MODULE_INVALID",
                    "Posting request source module must be pos, purchase, inventory, or payment");
        }
    }

    private void validateOptionalStatus(String status) {
        if (status != null && !REQUEST_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POSTING_REQUEST_STATUS_INVALID",
                    "Invalid posting request status");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAGE_LIMIT_INVALID",
                    "Limit must be between 1 and 200");
        }
        return limit;
    }

    private int normalizeOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAGE_OFFSET_INVALID",
                    "Offset must be zero or greater");
        }
        return offset;
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private void requireBranchIfPresent(int companyId, Integer branchId) {
        if (branchId == null) {
            return;
        }
        if (!dbFinanceSetup.branchBelongsToCompany(companyId, branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_BRANCH_SCOPE_INVALID",
                    "Branch does not belong to the requested company");
        }
    }

    private void authorizeRead(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.read");
    }

    private void authorizeCreate(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.create");
    }

    private void authorizeEdit(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.edit");
    }
}
