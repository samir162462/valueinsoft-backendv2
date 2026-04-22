package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceReconciliation;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationItemItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationRunItem;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceImportResponse;
import com.example.valueinsoftbackend.Model.Finance.FinanceReconciliationSourceItem;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationItemResolutionRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationRunCreateRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportItemRequest;
import com.example.valueinsoftbackend.Model.Request.Finance.FinanceReconciliationSourceImportRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FinanceReconciliationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final Set<String> RECONCILIATION_TYPES = Set.of(
            "cash_drawer",
            "card_settlement",
            "bank",
            "inventory_valuation",
            "supplier",
            "customer");
    private static final Set<String> RUN_STATUSES = Set.of(
            "pending",
            "running",
            "completed",
            "completed_with_exceptions",
            "failed",
            "cancelled");
    private static final Set<String> MATCH_STATUSES = Set.of(
            "matched",
            "unmatched_source",
            "unmatched_ledger",
            "difference",
            "ignored");
    private static final Set<String> RESOLUTION_STATUSES = Set.of(
            "unresolved",
            "proposed",
            "resolved",
            "dismissed");

    private final DbFinanceReconciliation dbFinanceReconciliation;
    private final DbFinanceSetup dbFinanceSetup;
    private final AuthorizationService authorizationService;
    private final FinanceAuditService financeAuditService;
    private final ObjectMapper objectMapper;

    public FinanceReconciliationService(DbFinanceReconciliation dbFinanceReconciliation,
                                        DbFinanceSetup dbFinanceSetup,
                                        AuthorizationService authorizationService,
                                        FinanceAuditService financeAuditService,
                                        ObjectMapper objectMapper) {
        this.dbFinanceReconciliation = dbFinanceReconciliation;
        this.dbFinanceSetup = dbFinanceSetup;
        this.authorizationService = authorizationService;
        this.financeAuditService = financeAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FinanceReconciliationRunItem createRunForAuthenticatedUser(String authenticatedName,
                                                                      FinanceReconciliationRunCreateRequest request) {
        validateCreateRequest(request);
        authorizeEdit(authenticatedName, request.getCompanyId(), request.getBranchId());

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        FinanceReconciliationRunItem run = dbFinanceReconciliation.createRun(request, actorUserId);
        List<String> mappingKeys = mappingKeysForType(request.getReconciliationType());
        dbFinanceReconciliation.createMatchedSourceItems(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                mappingKeys,
                actorUserId);
        dbFinanceReconciliation.createUnmatchedSourceItems(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                actorUserId);
        dbFinanceReconciliation.refreshSourceItemStatusesForRun(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                actorUserId);
        dbFinanceReconciliation.createUnmatchedLedgerItems(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                request.getBranchId(),
                request.getPeriodStart(),
                request.getPeriodEnd(),
                mappingKeys,
                actorUserId);
        FinanceReconciliationRunItem completed = dbFinanceReconciliation.completeRun(
                request.getCompanyId(),
                run.getReconciliationRunId(),
                actorUserId);

        financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                "finance.reconciliation.run.completed",
                "finance_reconciliation_run",
                completed.getReconciliationRunId().toString(),
                Map.of(
                        "reconciliationType", completed.getReconciliationType(),
                        "periodStart", completed.getPeriodStart().toString(),
                        "periodEnd", completed.getPeriodEnd().toString(),
                        "status", completed.getStatus(),
                        "differenceAmount", completed.getDifferenceAmount()),
                "Finance reconciliation run completed");
        return completed;
    }

    @Transactional
    public FinanceReconciliationSourceImportResponse importSourceItemsForAuthenticatedUser(String authenticatedName,
                                                                                          FinanceReconciliationSourceImportRequest request) {
        validateSourceImportRequest(request);
        authorizeEdit(authenticatedName, request.getCompanyId(), request.getBranchId());

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        ArrayList<String> rawPayloadJson = new ArrayList<>();
        for (FinanceReconciliationSourceImportItemRequest item : request.getItems()) {
            rawPayloadJson.add(toJson(item.getRawPayload()));
        }
        ArrayList<FinanceReconciliationSourceItem> imported = dbFinanceReconciliation.importSourceItems(
                request.getCompanyId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getSourceSystem(),
                request.getItems(),
                rawPayloadJson,
                actorUserId);

        FinanceReconciliationSourceImportResponse response = new FinanceReconciliationSourceImportResponse(
                request.getCompanyId(),
                request.getBranchId(),
                request.getReconciliationType(),
                request.getSourceSystem(),
                imported.size(),
                java.time.Instant.now(),
                imported);
        financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                "finance.reconciliation.source_items.imported",
                "finance_reconciliation_source_item",
                request.getSourceSystem(),
                Map.of(
                        "reconciliationType", request.getReconciliationType(),
                        "sourceSystem", request.getSourceSystem(),
                        "importedCount", imported.size()),
                "Finance reconciliation source items imported");
        return response;
    }

    public ArrayList<FinanceReconciliationSourceItem> getSourceItemsForAuthenticatedUser(String authenticatedName,
                                                                                        int companyId,
                                                                                        Integer branchId,
                                                                                        String reconciliationType,
                                                                                        String sourceSystem,
                                                                                        String status,
                                                                                        Integer limit,
                                                                                        Integer offset) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId, branchId);

        String normalizedType = normalizeOptional(reconciliationType);
        if (normalizedType != null && !RECONCILIATION_TYPES.contains(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        String normalizedSourceSystem = normalizeRequiredIdentifier(sourceSystem, "FINANCE_RECONCILIATION_SOURCE_SYSTEM_INVALID",
                false);
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !Set.of("imported", "matched", "exception", "ignored").contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_STATUS_INVALID",
                    "Invalid reconciliation source item status");
        }

        return dbFinanceReconciliation.getSourceItems(
                companyId,
                branchId,
                normalizedType,
                normalizedSourceSystem,
                normalizedStatus,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public ArrayList<FinanceReconciliationRunItem> getRunsForAuthenticatedUser(String authenticatedName,
                                                                               int companyId,
                                                                               Integer branchId,
                                                                               String reconciliationType,
                                                                               String status,
                                                                               Integer limit,
                                                                               Integer offset) {
        requireCompany(companyId);
        requireBranchIfPresent(companyId, branchId);
        authorizeRead(authenticatedName, companyId, branchId);

        String normalizedType = normalizeOptional(reconciliationType);
        if (normalizedType != null && !RECONCILIATION_TYPES.contains(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !RUN_STATUSES.contains(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_STATUS_INVALID",
                    "Invalid reconciliation run status");
        }

        return dbFinanceReconciliation.getRuns(
                companyId,
                branchId,
                normalizedType,
                normalizedStatus,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    public FinanceReconciliationRunItem getRunForAuthenticatedUser(String authenticatedName,
                                                                   int companyId,
                                                                   UUID reconciliationRunId) {
        requireCompany(companyId);
        authorizeRead(authenticatedName, companyId, null);
        return requireRun(companyId, reconciliationRunId);
    }

    public ArrayList<FinanceReconciliationItemItem> getItemsForAuthenticatedUser(String authenticatedName,
                                                                                 int companyId,
                                                                                 UUID reconciliationRunId,
                                                                                 String matchStatus,
                                                                                 String resolutionStatus,
                                                                                 Integer limit,
                                                                                 Integer offset) {
        requireCompany(companyId);
        FinanceReconciliationRunItem run = requireRun(companyId, reconciliationRunId);
        authorizeRead(authenticatedName, companyId, run.getBranchId());

        String normalizedMatchStatus = normalizeOptional(matchStatus);
        if (normalizedMatchStatus != null && !MATCH_STATUSES.contains(normalizedMatchStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_MATCH_STATUS_INVALID",
                    "Invalid reconciliation match status");
        }
        String normalizedResolutionStatus = normalizeOptional(resolutionStatus);
        if (normalizedResolutionStatus != null && !RESOLUTION_STATUSES.contains(normalizedResolutionStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_RESOLUTION_STATUS_INVALID",
                    "Invalid reconciliation resolution status");
        }

        return dbFinanceReconciliation.getItems(
                companyId,
                reconciliationRunId,
                normalizedMatchStatus,
                normalizedResolutionStatus,
                normalizeLimit(limit),
                normalizeOffset(offset));
    }

    @Transactional
    public FinanceReconciliationItemItem updateItemResolutionForAuthenticatedUser(String authenticatedName,
                                                                                  UUID reconciliationRunId,
                                                                                  UUID reconciliationItemId,
                                                                                  FinanceReconciliationItemResolutionRequest request) {
        requireCompany(request.getCompanyId());
        FinanceReconciliationItemItem item = requireItem(request.getCompanyId(), reconciliationItemId);
        if (!item.getReconciliationRunId().equals(reconciliationRunId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_ITEM_RUN_MISMATCH",
                    "Reconciliation item does not belong to the requested run");
        }
        FinanceReconciliationRunItem run = requireRun(request.getCompanyId(), item.getReconciliationRunId());
        authorizeEdit(authenticatedName, request.getCompanyId(), run.getBranchId());

        String resolutionStatus = normalizeOptional(request.getResolutionStatus());
        if (resolutionStatus == null || !RESOLUTION_STATUSES.contains(resolutionStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_RESOLUTION_STATUS_INVALID",
                    "Invalid reconciliation resolution status");
        }
        String note = request.getResolutionNote() == null ? null : request.getResolutionNote().trim();
        if (("resolved".equals(resolutionStatus) || "dismissed".equals(resolutionStatus))
                && (note == null || note.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_RESOLUTION_NOTE_REQUIRED",
                    "Resolved or dismissed reconciliation items require a resolution note");
        }

        Integer actorUserId = financeAuditService.resolveActorUserId(authenticatedName);
        FinanceReconciliationItemItem updated = dbFinanceReconciliation.updateItemResolution(
                request.getCompanyId(),
                reconciliationItemId,
                resolutionStatus,
                note,
                actorUserId);
        dbFinanceReconciliation.refreshRunDifference(
                request.getCompanyId(),
                updated.getReconciliationRunId(),
                actorUserId);

        financeAuditService.recordEvent(
                authenticatedName,
                request.getCompanyId(),
                run.getBranchId(),
                "finance.reconciliation.item.resolution_updated",
                "finance_reconciliation_item",
                updated.getReconciliationItemId().toString(),
                Map.of(
                        "reconciliationRunId", updated.getReconciliationRunId().toString(),
                        "resolutionStatus", updated.getResolutionStatus(),
                        "matchStatus", updated.getMatchStatus()),
                "Finance reconciliation item resolution updated");
        return updated;
    }

    private void validateCreateRequest(FinanceReconciliationRunCreateRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_REQUEST_REQUIRED",
                    "Reconciliation run request is required");
        }
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        request.setReconciliationType(normalizeOptional(request.getReconciliationType()));
        if (request.getReconciliationType() == null || !RECONCILIATION_TYPES.contains(request.getReconciliationType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        if (request.getPeriodStart() == null || request.getPeriodEnd() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PERIOD_REQUIRED",
                    "Reconciliation period start and end are required");
        }
        if (request.getPeriodStart().isAfter(request.getPeriodEnd())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PERIOD_INVALID",
                    "Reconciliation period start cannot be after period end");
        }
        if (request.getPeriodEnd().isAfter(LocalDate.now().plusDays(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_PERIOD_INVALID",
                    "Reconciliation period end is too far in the future");
        }
    }

    private void validateSourceImportRequest(FinanceReconciliationSourceImportRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_IMPORT_REQUIRED",
                    "Reconciliation source import request is required");
        }
        requireCompany(request.getCompanyId());
        requireBranchIfPresent(request.getCompanyId(), request.getBranchId());
        request.setReconciliationType(normalizeOptional(request.getReconciliationType()));
        if (request.getReconciliationType() == null || !RECONCILIATION_TYPES.contains(request.getReconciliationType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        }
        request.setSourceSystem(normalizeRequiredIdentifier(request.getSourceSystem(),
                "FINANCE_RECONCILIATION_SOURCE_SYSTEM_INVALID", true));
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_ITEMS_REQUIRED",
                    "At least one reconciliation source item is required");
        }
        if (request.getItems().size() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_ITEMS_TOO_MANY",
                    "A single import request cannot exceed 500 source items");
        }
        for (FinanceReconciliationSourceImportItemRequest item : request.getItems()) {
            validateSourceImportItem(item);
        }
    }

    private void validateSourceImportItem(FinanceReconciliationSourceImportItemRequest item) {
        if (item == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_ITEM_INVALID",
                    "Reconciliation source item is required");
        }
        item.setExternalReference(normalizeRequiredIdentifier(item.getExternalReference(),
                "FINANCE_RECONCILIATION_EXTERNAL_REFERENCE_INVALID", true));
        if (item.getSourceDate() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_DATE_REQUIRED",
                    "Reconciliation source item date is required");
        }
        if (item.getAmount() == null || item.getAmount().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_AMOUNT_INVALID",
                    "Reconciliation source item amount must be zero or positive");
        }
        item.setAmount(item.getAmount().setScale(4, java.math.RoundingMode.HALF_UP));
        if (item.getCurrencyCode() == null || !item.getCurrencyCode().matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }
        if (item.getDescription() != null) {
            item.setDescription(item.getDescription().trim());
        }
    }

    private FinanceReconciliationRunItem requireRun(int companyId, UUID reconciliationRunId) {
        try {
            return dbFinanceReconciliation.getRunById(companyId, reconciliationRunId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_RECONCILIATION_RUN_NOT_FOUND",
                    "Finance reconciliation run was not found");
        }
    }

    private FinanceReconciliationItemItem requireItem(int companyId, UUID reconciliationItemId) {
        try {
            return dbFinanceReconciliation.getItemById(companyId, reconciliationItemId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_RECONCILIATION_ITEM_NOT_FOUND",
                    "Finance reconciliation item was not found");
        }
    }

    private List<String> mappingKeysForType(String reconciliationType) {
        return switch (reconciliationType) {
            case "cash_drawer" -> List.of("pos.cash", "payment.cash_drawer", "payment.cash_safe");
            case "card_settlement" -> List.of("pos.card", "payment.card_clearing", "payment.fee_expense");
            case "bank" -> List.of("payment.bank", "purchase.bank");
            case "inventory_valuation" -> List.of("inventory.asset", "purchase.inventory", "pos.inventory");
            case "supplier" -> List.of("purchase.payable", "purchase.grni");
            case "customer" -> List.of("pos.receivable");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_TYPE_INVALID",
                    "Invalid reconciliation type");
        };
    }

    private void requireCompany(int companyId) {
        if (!dbFinanceSetup.companyExists(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FINANCE_COMPANY_NOT_FOUND",
                    "Company does not exist");
        }
    }

    private void requireBranchIfPresent(int companyId, Integer branchId) {
        if (branchId != null && !dbFinanceSetup.branchBelongsToCompany(companyId, branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_BRANCH_INVALID",
                    "Branch does not belong to the company");
        }
    }

    private void authorizeRead(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.read");
    }

    private void authorizeEdit(String authenticatedName, int companyId, Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                companyId,
                branchId,
                "finance.entry.edit");
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeRequiredIdentifier(String value, String errorCode, boolean required) {
        if (value == null || value.trim().isEmpty()) {
            if (required) {
                throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "Required identifier is missing");
            }
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("^[a-z0-9_.:-]{1,128}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode,
                    "Identifier may contain letters, numbers, underscore, dot, colon, or dash only");
        }
        return normalized;
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_RECONCILIATION_SOURCE_PAYLOAD_INVALID",
                    "Reconciliation source item raw payload is not valid JSON");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private int normalizeOffset(Integer offset) {
        return offset == null ? 0 : Math.max(offset, 0);
    }
}
