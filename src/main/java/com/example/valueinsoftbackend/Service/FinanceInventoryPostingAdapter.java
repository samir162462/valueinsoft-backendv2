package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountMappingItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class FinanceInventoryPostingAdapter implements FinancePostingAdapter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(8);
    private static final Set<String> INVENTORY_SOURCE_TYPES = Set.of(
            "adjustment",
            "inventory_adjustment",
            "stock_adjustment",
            "stock_count",
            "damage",
            "damaged",
            "write_off",
            "writeoff",
            "stock_transfer",
            "branch_transfer",
            "interbranch_transfer");

    private final DbFinanceSetup dbFinanceSetup;
    private final DbFinanceJournal dbFinanceJournal;
    private final ObjectMapper objectMapper;

    public FinanceInventoryPostingAdapter(DbFinanceSetup dbFinanceSetup,
            DbFinanceJournal dbFinanceJournal,
            ObjectMapper objectMapper) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbFinanceJournal = dbFinanceJournal;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceModule) {
        return "inventory".equals(sourceModule);
    }

    @Override
    public UUID post(FinancePostingRequestItem request) {
        if (!INVENTORY_SOURCE_TYPES.contains(request.getSourceType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_SOURCE_TYPE_UNSUPPORTED",
                    "Inventory posting adapter currently supports adjustment, damage, write-off, and stock transfer source types only");
        }

        JsonNode payload = parsePayload(request);
        if (isStockTransferSourceType(request.getSourceType())) {
            return postStockTransfer(request, payload);
        }

        return postInventoryAdjustment(request, payload);
    }

    private UUID postInventoryAdjustment(FinancePostingRequestItem request, JsonNode payload) {
        String currencyCode = text(payload, "currencyCode", "EGP");
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }

        String direction = normalizeDirection(payload, request);
        String reasonCode = text(payload, "reasonCode", null);
        String reason = text(payload, "reason", null);
        if ((reasonCode == null || reasonCode.isBlank()) && (reason == null || reason.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_REASON_REQUIRED",
                    "Inventory adjustment posting requires reasonCode or reason");
        }

        BigDecimal adjustmentAmount = firstOptionalAmount(payload,
                "adjustmentAmount",
                "inventoryAmount",
                "valuationAmount",
                "totalCost",
                "amount");
        List<InventoryAdjustmentLine> adjustmentLines = adjustmentLinesFromItems(payload, direction);
        BigDecimal itemAdjustmentAmount = ZERO;
        for (InventoryAdjustmentLine adjustmentLine : adjustmentLines) {
            itemAdjustmentAmount = itemAdjustmentAmount.add(adjustmentLine.amount());
        }

        if (adjustmentAmount.compareTo(ZERO) > 0
                && itemAdjustmentAmount.compareTo(ZERO) > 0
                && adjustmentAmount.compareTo(itemAdjustmentAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_TOTAL_MISMATCH",
                    "Inventory adjustment amount must match item valuation total when both are provided");
        }
        if (itemAdjustmentAmount.compareTo(ZERO) == 0 && adjustmentAmount.compareTo(ZERO) > 0) {
            adjustmentLines = List.of(new InventoryAdjustmentLine(
                    adjustmentAmount,
                    longValue(payload, "productId"),
                    longValue(payload, "inventoryMovementId")));
            itemAdjustmentAmount = adjustmentAmount;
        }
        if (itemAdjustmentAmount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_AMOUNT_REQUIRED",
                    "Inventory adjustment posting requires a positive valuation amount");
        }

        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        FinanceAccountMappingItem inventoryMapping = resolveMapping(request, "inventory.asset");
        FinanceAccountMappingItem offsetMapping = resolveMapping(request, offsetMappingKey(direction, request));
        for (InventoryAdjustmentLine adjustmentLine : adjustmentLines) {
            if ("increase".equals(direction)) {
                lines.add(debitLine(
                        inventoryMapping,
                        request,
                        adjustmentLine.amount(),
                        "Inventory adjustment increase",
                        adjustmentLine.productId(),
                        adjustmentLine.inventoryMovementId()));
                lines.add(creditLine(
                        offsetMapping,
                        request,
                        adjustmentLine.amount(),
                        "Inventory adjustment gain",
                        adjustmentLine.productId(),
                        adjustmentLine.inventoryMovementId()));
            } else {
                lines.add(debitLine(
                        offsetMapping,
                        request,
                        adjustmentLine.amount(),
                        inventoryExpenseDescription(request),
                        adjustmentLine.productId(),
                        adjustmentLine.inventoryMovementId()));
                lines.add(creditLine(
                        inventoryMapping,
                        request,
                        adjustmentLine.amount(),
                        "Inventory adjustment decrease",
                        adjustmentLine.productId(),
                        adjustmentLine.inventoryMovementId()));
            }
        }

        BigDecimal totalDebit = totalDebit(lines);
        BigDecimal totalCredit = totalCredit(lines);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_JOURNAL_UNBALANCED",
                    "Inventory adjustment posting lines are not balanced");
        }

        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                "inventory.adjustment",
                "IA-");
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }

        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        request.getCompanyId(),
                        request.getBranchId(),
                        journalNumber,
                        "inventory",
                        request.getSourceModule(),
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getPostingDate(),
                        request.getFiscalPeriodId(),
                        "Inventory adjustment " + request.getSourceId(),
                        currencyCode,
                        ONE,
                        totalDebit,
                        totalCredit,
                        postedBy,
                        lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private UUID postStockTransfer(FinancePostingRequestItem request, JsonNode payload) {
        String currencyCode = text(payload, "currencyCode", "EGP");
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }

        Integer sourceBranchId = integerValue(payload, "sourceBranchId");
        Integer destinationBranchId = integerValue(payload, "destinationBranchId");
        if (sourceBranchId == null || destinationBranchId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_BRANCH_REQUIRED",
                    "Stock transfer posting requires sourceBranchId and destinationBranchId");
        }
        if (sourceBranchId.equals(destinationBranchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_BRANCH_CONFLICT",
                    "Stock transfer source and destination branch must differ");
        }

        BigDecimal transferAmount = firstOptionalAmount(payload,
                "transferAmount",
                "inventoryAmount",
                "valuationAmount",
                "amount");
        List<StockTransferLine> transferLines = stockTransferLinesFromItems(payload, sourceBranchId, destinationBranchId);
        BigDecimal itemTransferAmount = ZERO;
        for (StockTransferLine transferLine : transferLines) {
            itemTransferAmount = itemTransferAmount.add(transferLine.amount());
        }

        if (transferAmount.compareTo(ZERO) > 0
                && itemTransferAmount.compareTo(ZERO) > 0
                && transferAmount.compareTo(itemTransferAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_TOTAL_MISMATCH",
                    "Stock transfer amount must match item valuation total when both are provided");
        }
        if (itemTransferAmount.compareTo(ZERO) == 0 && transferAmount.compareTo(ZERO) > 0) {
            transferLines = List.of(new StockTransferLine(
                    transferAmount,
                    sourceBranchId,
                    destinationBranchId,
                    longValue(payload, "productId"),
                    longValue(payload, "inventoryMovementId"),
                    firstOptionalLong(payload, "destinationInventoryMovementId", "toInventoryMovementId", "inboundInventoryMovementId")));
            itemTransferAmount = transferAmount;
        }
        if (itemTransferAmount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_AMOUNT_REQUIRED",
                    "Stock transfer posting requires a positive valuation amount");
        }

        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        for (StockTransferLine transferLine : transferLines) {
            FinanceAccountMappingItem sourceInventoryMapping = resolveMapping(request, transferLine.sourceBranchId(), "inventory.asset");
            FinanceAccountMappingItem destinationInventoryMapping = resolveMapping(request, transferLine.destinationBranchId(), "inventory.asset");
            lines.add(new DbFinanceJournal.PostedSourceJournalLineCommand(
                    destinationInventoryMapping.getAccountId(),
                    transferLine.destinationBranchId(),
                    transferLine.amount(),
                    ZERO,
                    "Branch stock transfer receipt",
                    null,
                    null,
                    transferLine.productId(),
                    transferLine.destinationInventoryMovementId(),
                    null,
                    null,
                    null));
            lines.add(new DbFinanceJournal.PostedSourceJournalLineCommand(
                    sourceInventoryMapping.getAccountId(),
                    transferLine.sourceBranchId(),
                    ZERO,
                    transferLine.amount(),
                    "Branch stock transfer issue",
                    null,
                    null,
                    transferLine.productId(),
                    transferLine.sourceInventoryMovementId(),
                    null,
                    null,
                    null));
        }

        BigDecimal totalDebit = totalDebit(lines);
        BigDecimal totalCredit = totalCredit(lines);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_JOURNAL_UNBALANCED",
                    "Stock transfer posting lines are not balanced");
        }

        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                "inventory.transfer",
                "BT-");
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }

        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        request.getCompanyId(),
                        null,
                        journalNumber,
                        "inventory",
                        request.getSourceModule(),
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getPostingDate(),
                        request.getFiscalPeriodId(),
                        "Branch stock transfer " + request.getSourceId(),
                        currencyCode,
                        ONE,
                        totalDebit,
                        totalCredit,
                        postedBy,
                        lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private List<InventoryAdjustmentLine> adjustmentLinesFromItems(JsonNode payload, String headerDirection) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }

        ArrayList<InventoryAdjustmentLine> adjustmentLines = new ArrayList<>();
        for (JsonNode item : items) {
            String itemDirection = normalizeDirection(item, headerDirection);
            if (!headerDirection.equals(itemDirection)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_DIRECTION_MISMATCH",
                        "Inventory adjustment items must use the same direction as the posting header");
            }
            BigDecimal totalCost = optionalAmount(item, "totalCost");
            if (totalCost.compareTo(ZERO) == 0) {
                BigDecimal valuationAmount = firstOptionalAmount(item, "valuationAmount", "adjustmentAmount", "amount");
                if (valuationAmount.compareTo(ZERO) > 0) {
                    totalCost = valuationAmount;
                }
            }
            if (totalCost.compareTo(ZERO) == 0) {
                BigDecimal quantity = abs(optionalDecimal(item, "quantity", 8));
                if (quantity.compareTo(ZERO) == 0) {
                    quantity = abs(optionalDecimal(item, "quantityDelta", 8));
                }
                BigDecimal unitCost = firstOptionalAmount(item, "unitCost", "cost");
                if (quantity.compareTo(ZERO) > 0 && unitCost.compareTo(ZERO) > 0) {
                    totalCost = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
                }
            }
            if (totalCost.compareTo(ZERO) == 0) {
                continue;
            }
            adjustmentLines.add(new InventoryAdjustmentLine(
                    totalCost,
                    longValue(item, "productId"),
                    firstOptionalLong(item, "inventoryMovementId", "stockLedgerId", "stock_ledger_id")));
        }
        return adjustmentLines;
    }

    private String normalizeDirection(JsonNode payload, FinancePostingRequestItem request) {
        String direction = text(payload, "direction", null);
        if (direction == null) {
            direction = text(payload, "adjustmentDirection", null);
        }
        if (direction == null) {
            BigDecimal quantityDelta = optionalDecimal(payload, "quantityDelta", 8);
            if (quantityDelta.compareTo(ZERO) > 0) {
                direction = "increase";
            } else if (quantityDelta.compareTo(ZERO) < 0) {
                direction = "decrease";
            }
        }
        if (direction == null) {
            direction = directionFromItems(payload);
        }
        if (direction == null && isDecreaseSourceType(request.getSourceType())) {
            direction = "decrease";
        }
        return normalizeDirectionValue(direction);
    }

    private String directionFromItems(JsonNode payload) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return null;
        }

        String inferredDirection = null;
        for (JsonNode item : items) {
            String itemDirection = text(item, "direction", null);
            if (itemDirection == null) {
                itemDirection = text(item, "adjustmentDirection", null);
            }
            if (itemDirection == null) {
                BigDecimal quantityDelta = optionalDecimal(item, "quantityDelta", 8);
                if (quantityDelta.compareTo(ZERO) > 0) {
                    itemDirection = "increase";
                } else if (quantityDelta.compareTo(ZERO) < 0) {
                    itemDirection = "decrease";
                }
            }
            if (itemDirection == null) {
                continue;
            }
            itemDirection = normalizeDirectionValue(itemDirection);
            if (inferredDirection == null) {
                inferredDirection = itemDirection;
            } else if (!inferredDirection.equals(itemDirection)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_DIRECTION_MISMATCH",
                        "Inventory adjustment items must use one direction per posting request");
            }
        }
        return inferredDirection;
    }

    private String normalizeDirection(JsonNode item, String defaultDirection) {
        String direction = text(item, "direction", null);
        if (direction == null) {
            direction = text(item, "adjustmentDirection", null);
        }
        if (direction == null) {
            BigDecimal quantityDelta = optionalDecimal(item, "quantityDelta", 8);
            if (quantityDelta.compareTo(ZERO) > 0) {
                direction = "increase";
            } else if (quantityDelta.compareTo(ZERO) < 0) {
                direction = "decrease";
            }
        }
        return direction == null ? defaultDirection : normalizeDirectionValue(direction);
    }

    private String normalizeDirectionValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_DIRECTION_REQUIRED",
                    "Inventory adjustment posting requires direction increase or decrease");
        }
        String direction = value.trim().toLowerCase();
        if ("in".equals(direction) || "stock_in".equals(direction) || "increase".equals(direction)
                || "gain".equals(direction) || "positive".equals(direction)) {
            return "increase";
        }
        if ("out".equals(direction) || "stock_out".equals(direction) || "decrease".equals(direction)
                || "loss".equals(direction) || "negative".equals(direction) || "damage".equals(direction)
                || "write_off".equals(direction) || "writeoff".equals(direction)) {
            return "decrease";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_DIRECTION_INVALID",
                "Inventory adjustment direction must be increase or decrease");
    }

    private String offsetMappingKey(String direction, FinancePostingRequestItem request) {
        if ("increase".equals(direction)) {
            return "inventory.adjustment_gain";
        }
        if ("damage".equals(request.getSourceType()) || "damaged".equals(request.getSourceType())) {
            return "inventory.damage_expense";
        }
        if ("write_off".equals(request.getSourceType()) || "writeoff".equals(request.getSourceType())) {
            return "inventory.writeoff_expense";
        }
        return "inventory.adjustment_expense";
    }

    private String inventoryExpenseDescription(FinancePostingRequestItem request) {
        if ("damage".equals(request.getSourceType()) || "damaged".equals(request.getSourceType())) {
            return "Inventory damage expense";
        }
        if ("write_off".equals(request.getSourceType()) || "writeoff".equals(request.getSourceType())) {
            return "Inventory write-off expense";
        }
        return "Inventory adjustment expense";
    }

    private boolean isDecreaseSourceType(String sourceType) {
        return "damage".equals(sourceType)
                || "damaged".equals(sourceType)
                || "write_off".equals(sourceType)
                || "writeoff".equals(sourceType);
    }

    private FinanceAccountMappingItem resolveMapping(FinancePostingRequestItem request, String mappingKey) {
        return resolveMapping(request, request.getBranchId(), mappingKey);
    }

    private FinanceAccountMappingItem resolveMapping(FinancePostingRequestItem request, Integer branchId, String mappingKey) {
        FinanceAccountMappingItem mapping = dbFinanceSetup.resolveActiveAccountMapping(
                request.getCompanyId(),
                branchId,
                mappingKey,
                request.getPostingDate());
        if (mapping == null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_ACCOUNT_MAPPING_MISSING",
                    "Missing active finance account mapping: " + mappingKey);
        }
        return mapping;
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand debitLine(FinanceAccountMappingItem mapping,
            FinancePostingRequestItem request,
            BigDecimal amount,
            String description,
            Long productId,
            Long inventoryMovementId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                amount,
                ZERO,
                description,
                null,
                null,
                productId,
                inventoryMovementId,
                null,
                null,
                null);
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand creditLine(FinanceAccountMappingItem mapping,
            FinancePostingRequestItem request,
            BigDecimal amount,
            String description,
            Long productId,
            Long inventoryMovementId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                ZERO,
                amount,
                description,
                null,
                null,
                productId,
                inventoryMovementId,
                null,
                null,
                null);
    }

    private JsonNode parsePayload(FinancePostingRequestItem request) {
        try {
            return objectMapper.readTree(request.getRequestPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_PAYLOAD_INVALID",
                    "Inventory posting payload is not valid JSON");
        }
    }

    private BigDecimal firstOptionalAmount(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal amount = optionalAmount(node, field);
            if (amount.compareTo(ZERO) > 0) {
                return amount;
            }
        }
        return ZERO;
    }

    private BigDecimal optionalAmount(JsonNode node, String field) {
        BigDecimal amount = optionalDecimal(node, field, 4);
        if (amount.compareTo(ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_AMOUNT_INVALID",
                    "Inventory amount cannot be negative: " + field);
        }
        return amount;
    }

    private BigDecimal optionalDecimal(JsonNode node, String field, int scale) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return ZERO;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_AMOUNT_INVALID",
                    "Inventory amount must be numeric: " + field);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.asText()).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_AMOUNT_INVALID",
                    "Inventory amount must be numeric: " + field);
        }
        return amount;
    }

    private BigDecimal abs(BigDecimal amount) {
        return amount.compareTo(ZERO) < 0 ? amount.negate() : amount;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText().trim();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_IDENTIFIER_INVALID",
                    "Inventory identifier must be numeric: " + field);
        }
        try {
            long longValue = Long.parseLong(value.asText());
            return longValue > 0 ? longValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_IDENTIFIER_INVALID",
                    "Inventory identifier must be numeric: " + field);
        }
    }

    private Long firstOptionalLong(JsonNode node, String... fields) {
        for (String field : fields) {
            Long value = longValue(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_IDENTIFIER_INVALID",
                    "Inventory identifier must be numeric: " + field);
        }
        try {
            int intValue = Integer.parseInt(value.asText());
            return intValue > 0 ? intValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_INVENTORY_IDENTIFIER_INVALID",
                    "Inventory identifier must be numeric: " + field);
        }
    }

    private List<StockTransferLine> stockTransferLinesFromItems(JsonNode payload,
                                                                Integer headerSourceBranchId,
                                                                Integer headerDestinationBranchId) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }

        ArrayList<StockTransferLine> transferLines = new ArrayList<>();
        for (JsonNode item : items) {
            Integer sourceBranchId = integerValue(item, "sourceBranchId");
            if (sourceBranchId == null) {
                sourceBranchId = headerSourceBranchId;
            }
            Integer destinationBranchId = integerValue(item, "destinationBranchId");
            if (destinationBranchId == null) {
                destinationBranchId = headerDestinationBranchId;
            }
            if (sourceBranchId == null || destinationBranchId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_BRANCH_REQUIRED",
                        "Each stock transfer item requires source and destination branch");
            }
            if (sourceBranchId.equals(destinationBranchId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_STOCK_TRANSFER_BRANCH_CONFLICT",
                        "Stock transfer source and destination branch must differ");
            }

            BigDecimal totalCost = optionalAmount(item, "totalCost");
            if (totalCost.compareTo(ZERO) == 0) {
                BigDecimal valuationAmount = firstOptionalAmount(item, "transferAmount", "valuationAmount", "amount");
                if (valuationAmount.compareTo(ZERO) > 0) {
                    totalCost = valuationAmount;
                }
            }
            if (totalCost.compareTo(ZERO) == 0) {
                BigDecimal quantity = abs(optionalDecimal(item, "quantity", 8));
                BigDecimal unitCost = firstOptionalAmount(item, "unitCost", "cost");
                if (quantity.compareTo(ZERO) > 0 && unitCost.compareTo(ZERO) > 0) {
                    totalCost = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
                }
            }
            if (totalCost.compareTo(ZERO) == 0) {
                continue;
            }
            transferLines.add(new StockTransferLine(
                    totalCost,
                    sourceBranchId,
                    destinationBranchId,
                    longValue(item, "productId"),
                    firstOptionalLong(item, "sourceInventoryMovementId", "inventoryMovementId", "outboundInventoryMovementId", "stockLedgerId"),
                    firstOptionalLong(item, "destinationInventoryMovementId", "toInventoryMovementId", "inboundInventoryMovementId")));
        }
        return transferLines;
    }

    private boolean isStockTransferSourceType(String sourceType) {
        return "stock_transfer".equals(sourceType)
                || "branch_transfer".equals(sourceType)
                || "interbranch_transfer".equals(sourceType);
    }

    private BigDecimal totalDebit(List<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        BigDecimal total = ZERO;
        for (DbFinanceJournal.PostedSourceJournalLineCommand line : lines) {
            total = total.add(line.debitAmount());
        }
        return total;
    }

    private BigDecimal totalCredit(List<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        BigDecimal total = ZERO;
        for (DbFinanceJournal.PostedSourceJournalLineCommand line : lines) {
            total = total.add(line.creditAmount());
        }
        return total;
    }

    private record InventoryAdjustmentLine(BigDecimal amount, Long productId, Long inventoryMovementId) {
    }

    private record StockTransferLine(BigDecimal amount,
                                     Integer sourceBranchId,
                                     Integer destinationBranchId,
                                     Long productId,
                                     Long sourceInventoryMovementId,
                                     Long destinationInventoryMovementId) {
    }
}
