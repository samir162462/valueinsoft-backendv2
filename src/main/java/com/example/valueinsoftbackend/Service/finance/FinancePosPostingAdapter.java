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
public class FinancePosPostingAdapter implements FinancePostingAdapter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(8);
    private static final Set<String> SALE_SOURCE_TYPES = Set.of("sale", "pos_sale", "order", "pos_order");
    private static final Set<String> RETURN_SOURCE_TYPES = Set.of(
            "sale_return",
            "pos_sale_return",
            "return",
            "refund",
            "pos_return",
            "pos_refund",
            "bounce_back");

    private final DbFinanceSetup dbFinanceSetup;
    private final DbFinanceJournal dbFinanceJournal;
    private final ObjectMapper objectMapper;

    public FinancePosPostingAdapter(DbFinanceSetup dbFinanceSetup,
            DbFinanceJournal dbFinanceJournal,
            ObjectMapper objectMapper) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbFinanceJournal = dbFinanceJournal;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceModule) {
        return "pos".equals(sourceModule);
    }

    @Override
    public UUID post(FinancePostingRequestItem request) {
        if (!SALE_SOURCE_TYPES.contains(request.getSourceType())
                && !RETURN_SOURCE_TYPES.contains(request.getSourceType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_SOURCE_TYPE_UNSUPPORTED",
                    "POS posting adapter currently supports sale/order and sale return/refund source types only");
        }

        JsonNode payload = parsePayload(request);
        String currencyCode = text(payload, "currencyCode", "EGP");
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }
        if (RETURN_SOURCE_TYPES.contains(request.getSourceType())) {
            return postSaleReturn(request, payload, currencyCode);
        }

        BigDecimal netAmount = requiredAmount(payload, "netAmount");
        BigDecimal discountAmount = optionalAmount(payload, "discountAmount");
        BigDecimal taxAmount = optionalAmount(payload, "taxAmount");
        BigDecimal salesAmount = optionalAmount(payload, "salesAmount");
        if (salesAmount.compareTo(ZERO) == 0) {
            salesAmount = netAmount.add(discountAmount).subtract(taxAmount).setScale(4, RoundingMode.HALF_UP);
        }
        if (netAmount.compareTo(ZERO) <= 0 || salesAmount.compareTo(ZERO) <= 0 || taxAmount.compareTo(ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_AMOUNT_INVALID",
                    "POS sale amounts must be positive and non-negative where applicable");
        }

        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        addTenderLines(request, payload, netAmount, lines);
        if (discountAmount.compareTo(ZERO) > 0) {
            lines.add(debitLine(
                    resolveMapping(request, "pos.discount"),
                    request,
                    discountAmount,
                    "POS discount",
                    integerValue(payload, "customerId"),
                    null,
                    null,
                    null));
        }
        lines.add(creditLine(
                resolveMapping(request, "pos.sales"),
                request,
                salesAmount,
                "POS sales revenue",
                integerValue(payload, "customerId"),
                null,
                null,
                null));
        if (taxAmount.compareTo(ZERO) > 0) {
            lines.add(creditLine(
                    resolveMapping(request, "pos.output_vat"),
                    request,
                    taxAmount,
                    "POS output VAT",
                    integerValue(payload, "customerId"),
                    null,
                    null,
                    null));
        }
        addInventoryValuationLines(request, payload, lines);

        BigDecimal totalDebit = totalDebit(lines);
        BigDecimal totalCredit = totalCredit(lines);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_JOURNAL_UNBALANCED",
                    "POS sale posting lines are not balanced");
        }

        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                "pos.sales",
                "PS-");
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }

        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        request.getCompanyId(),
                        request.getBranchId(),
                        journalNumber,
                        "sales",
                        request.getSourceModule(),
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getPostingDate(),
                        request.getFiscalPeriodId(),
                        "POS sale " + request.getSourceId(),
                        currencyCode,
                        ONE,
                        totalDebit,
                        totalCredit,
                        postedBy,
                        lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private UUID postSaleReturn(FinancePostingRequestItem request, JsonNode payload, String currencyCode) {
        BigDecimal refundAmount = firstRequiredAmount(payload, "refundAmount", "netAmount", "returnAmount");
        BigDecimal taxAmount = optionalAmount(payload, "taxAmount");
        BigDecimal salesReturnAmount = firstOptionalAmount(payload,
                "salesReturnAmount",
                "salesAmount",
                "returnSalesAmount");
        if (salesReturnAmount.compareTo(ZERO) == 0) {
            salesReturnAmount = refundAmount.subtract(taxAmount).setScale(4, RoundingMode.HALF_UP);
        }
        if (refundAmount.compareTo(ZERO) <= 0 || salesReturnAmount.compareTo(ZERO) <= 0
                || taxAmount.compareTo(ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_RETURN_AMOUNT_INVALID",
                    "POS return amounts must be positive and non-negative where applicable");
        }
        if (salesReturnAmount.add(taxAmount).compareTo(refundAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_RETURN_TOTAL_MISMATCH",
                    "POS return sales amount plus tax amount must equal refund amount");
        }

        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        lines.add(debitLine(
                resolveMapping(request, "pos.sales_returns"),
                request,
                salesReturnAmount,
                "POS sales return",
                integerValue(payload, "customerId"),
                null,
                null,
                null));
        if (taxAmount.compareTo(ZERO) > 0) {
            lines.add(debitLine(
                    resolveMapping(request, "pos.output_vat"),
                    request,
                    taxAmount,
                    "POS output VAT reversal",
                    integerValue(payload, "customerId"),
                    null,
                    null,
                    null));
        }
        addRefundLines(request, payload, refundAmount, lines);
        addReturnedInventoryValuationLines(request, payload, lines);

        BigDecimal totalDebit = totalDebit(lines);
        BigDecimal totalCredit = totalCredit(lines);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_RETURN_JOURNAL_UNBALANCED",
                    "POS return posting lines are not balanced");
        }

        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                "pos.sales_return",
                "SR-");
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }

        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        request.getCompanyId(),
                        request.getBranchId(),
                        journalNumber,
                        "sales_return",
                        request.getSourceModule(),
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getPostingDate(),
                        request.getFiscalPeriodId(),
                        "POS sale return " + request.getSourceId(),
                        currencyCode,
                        ONE,
                        totalDebit,
                        totalCredit,
                        postedBy,
                        lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private void addTenderLines(FinancePostingRequestItem request,
            JsonNode payload,
            BigDecimal expectedNetAmount,
            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        JsonNode payments = payload.get("payments");
        if (payments != null && payments.isArray() && !payments.isEmpty()) {
            BigDecimal paymentTotal = ZERO;
            for (JsonNode payment : payments) {
                String method = normalizePaymentMethod(text(payment, "method", "cash"));
                BigDecimal amount = requiredAmount(payment, "amount");
                paymentTotal = paymentTotal.add(amount);
                lines.add(debitLine(
                        resolveMapping(request, "pos." + method),
                        request,
                        amount,
                        "POS " + method + " tender",
                        integerValue(payload, "customerId"),
                        null,
                        null,
                        text(payment, "paymentId", null)));
            }
            if (paymentTotal.compareTo(expectedNetAmount) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_PAYMENT_TOTAL_MISMATCH",
                        "POS payment lines must equal net amount");
            }
            return;
        }

        String method = normalizePaymentMethod(text(payload, "paymentMethod", "cash"));
        lines.add(debitLine(
                resolveMapping(request, "pos." + method),
                request,
                expectedNetAmount,
                "POS " + method + " tender",
                integerValue(payload, "customerId"),
                null,
                null,
                text(payload, "paymentId", null)));
    }

    private void addRefundLines(FinancePostingRequestItem request,
            JsonNode payload,
            BigDecimal expectedRefundAmount,
            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        JsonNode refunds = payload.get("refunds");
        if (refunds != null && refunds.isArray() && !refunds.isEmpty()) {
            BigDecimal refundTotal = ZERO;
            for (JsonNode refund : refunds) {
                String method = normalizePaymentMethod(text(refund, "method", "cash"));
                BigDecimal amount = requiredAmount(refund, "amount");
                refundTotal = refundTotal.add(amount);
                lines.add(creditLine(
                        resolveMapping(request, "pos." + method),
                        request,
                        amount,
                        "POS " + method + " refund",
                        integerValue(payload, "customerId"),
                        null,
                        null,
                        text(refund, "paymentId", null)));
            }
            if (refundTotal.compareTo(expectedRefundAmount) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_REFUND_TOTAL_MISMATCH",
                        "POS refund lines must equal refund amount");
            }
            return;
        }

        String method = normalizePaymentMethod(text(payload, "refundMethod", text(payload, "paymentMethod", "cash")));
        lines.add(creditLine(
                resolveMapping(request, "pos." + method),
                request,
                expectedRefundAmount,
                "POS " + method + " refund",
                integerValue(payload, "customerId"),
                null,
                null,
                text(payload, "paymentId", null)));
    }

    private void addInventoryValuationLines(FinancePostingRequestItem request,
            JsonNode payload,
            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        BigDecimal headerCogsAmount = firstOptionalAmount(payload,
                "cogsAmount",
                "costOfGoodsSoldAmount",
                "inventoryCostAmount");
        List<CogsLine> cogsLines = cogsLinesFromItems(payload);
        BigDecimal itemCogsAmount = ZERO;
        for (CogsLine cogsLine : cogsLines) {
            itemCogsAmount = itemCogsAmount.add(cogsLine.amount());
        }

        if (headerCogsAmount.compareTo(ZERO) > 0
                && itemCogsAmount.compareTo(ZERO) > 0
                && headerCogsAmount.compareTo(itemCogsAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_COGS_TOTAL_MISMATCH",
                    "POS COGS amount must match item cost total when both are provided");
        }

        if (itemCogsAmount.compareTo(ZERO) == 0 && headerCogsAmount.compareTo(ZERO) > 0) {
            cogsLines = List.of(new CogsLine(headerCogsAmount, null, longValue(payload, "inventoryMovementId")));
            itemCogsAmount = headerCogsAmount;
        }
        if (itemCogsAmount.compareTo(ZERO) == 0) {
            return;
        }

        FinanceAccountMappingItem cogsMapping = resolveMapping(request, "pos.cogs");
        FinanceAccountMappingItem inventoryMapping = resolveMapping(request, "pos.inventory");
        for (CogsLine cogsLine : cogsLines) {
            lines.add(debitLine(
                    cogsMapping,
                    request,
                    cogsLine.amount(),
                    "POS cost of goods sold",
                    integerValue(payload, "customerId"),
                    cogsLine.productId(),
                    cogsLine.inventoryMovementId(),
                    null));
            lines.add(creditLine(
                    inventoryMapping,
                    request,
                    cogsLine.amount(),
                    "POS inventory valuation relief",
                    integerValue(payload, "customerId"),
                    cogsLine.productId(),
                    cogsLine.inventoryMovementId(),
                    null));
        }
    }

    private void addReturnedInventoryValuationLines(FinancePostingRequestItem request,
            JsonNode payload,
            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        BigDecimal headerCogsAmount = firstOptionalAmount(payload,
                "cogsAmount",
                "costOfGoodsSoldAmount",
                "inventoryCostAmount",
                "returnedCostAmount");
        List<CogsLine> cogsLines = cogsLinesFromItems(payload);
        BigDecimal itemCogsAmount = ZERO;
        for (CogsLine cogsLine : cogsLines) {
            itemCogsAmount = itemCogsAmount.add(cogsLine.amount());
        }

        if (headerCogsAmount.compareTo(ZERO) > 0
                && itemCogsAmount.compareTo(ZERO) > 0
                && headerCogsAmount.compareTo(itemCogsAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_COGS_TOTAL_MISMATCH",
                    "POS return COGS amount must match item cost total when both are provided");
        }

        if (itemCogsAmount.compareTo(ZERO) == 0 && headerCogsAmount.compareTo(ZERO) > 0) {
            cogsLines = List.of(new CogsLine(headerCogsAmount, null, longValue(payload, "inventoryMovementId")));
            itemCogsAmount = headerCogsAmount;
        }
        if (itemCogsAmount.compareTo(ZERO) == 0) {
            return;
        }

        boolean returnedToStock = booleanValue(payload, "returnedToStock", true);
        FinanceAccountMappingItem cogsMapping = resolveMapping(request, "pos.cogs");
        FinanceAccountMappingItem debitMapping = returnedToStock
                ? resolveMapping(request, "pos.inventory")
                : resolveMapping(request, "inventory.damage_expense");
        String debitDescription = returnedToStock
                ? "POS returned inventory valuation"
                : "POS non-sellable return damage expense";
        for (CogsLine cogsLine : cogsLines) {
            lines.add(debitLine(
                    debitMapping,
                    request,
                    cogsLine.amount(),
                    debitDescription,
                    integerValue(payload, "customerId"),
                    cogsLine.productId(),
                    cogsLine.inventoryMovementId(),
                    null));
            lines.add(creditLine(
                    cogsMapping,
                    request,
                    cogsLine.amount(),
                    "POS returned COGS reversal",
                    integerValue(payload, "customerId"),
                    cogsLine.productId(),
                    cogsLine.inventoryMovementId(),
                    null));
        }
    }

    private List<CogsLine> cogsLinesFromItems(JsonNode payload) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }

        ArrayList<CogsLine> cogsLines = new ArrayList<>();
        for (JsonNode item : items) {
            BigDecimal totalCost = optionalAmount(item, "totalCost");
            if (totalCost.compareTo(ZERO) == 0) {
                BigDecimal quantity = optionalDecimal(item, "quantity", 8);
                BigDecimal unitCost = firstOptionalAmount(item, "unitCost", "cost");
                if (quantity.compareTo(ZERO) > 0 && unitCost.compareTo(ZERO) > 0) {
                    totalCost = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
                }
            }
            if (totalCost.compareTo(ZERO) == 0) {
                continue;
            }
            cogsLines.add(new CogsLine(
                    totalCost,
                    longValue(item, "productId"),
                    firstOptionalLong(item, "inventoryMovementId", "stockLedgerId", "stock_ledger_id")));
        }
        return cogsLines;
    }

    private FinanceAccountMappingItem resolveMapping(FinancePostingRequestItem request, String mappingKey) {
        FinanceAccountMappingItem mapping = dbFinanceSetup.resolveActiveAccountMapping(
                request.getCompanyId(),
                request.getBranchId(),
                null,
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
            Integer customerId,
            Long productId,
            Long inventoryMovementId,
            String paymentId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                amount,
                ZERO,
                description,
                customerId,
                null,
                productId,
                inventoryMovementId,
                paymentId,
                null,
                null);
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand creditLine(FinanceAccountMappingItem mapping,
            FinancePostingRequestItem request,
            BigDecimal amount,
            String description,
            Integer customerId,
            Long productId,
            Long inventoryMovementId,
            String paymentId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                ZERO,
                amount,
                description,
                customerId,
                null,
                productId,
                inventoryMovementId,
                paymentId,
                null,
                null);
    }

    private JsonNode parsePayload(FinancePostingRequestItem request) {
        try {
            return objectMapper.readTree(request.getRequestPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_PAYLOAD_INVALID",
                    "POS posting payload is not valid JSON");
        }
    }

    private BigDecimal requiredAmount(JsonNode node, String field) {
        BigDecimal amount = optionalAmount(node, field);
        if (amount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_AMOUNT_REQUIRED",
                    "Required POS amount is missing or not positive: " + field);
        }
        return amount;
    }

    private BigDecimal optionalAmount(JsonNode node, String field) {
        return optionalDecimal(node, field, 4);
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

    private BigDecimal firstRequiredAmount(JsonNode node, String... fields) {
        BigDecimal amount = firstOptionalAmount(node, fields);
        if (amount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_AMOUNT_REQUIRED",
                    "Required POS amount is missing or not positive: " + String.join("/", fields));
        }
        return amount;
    }

    private BigDecimal optionalDecimal(JsonNode node, String field, int scale) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return ZERO;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_AMOUNT_INVALID",
                    "POS amount must be numeric: " + field);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.asText()).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_AMOUNT_INVALID",
                    "POS amount must be numeric: " + field);
        }
        if (amount.compareTo(ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_AMOUNT_INVALID",
                    "POS amount cannot be negative: " + field);
        }
        return amount;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText().trim();
    }

    private boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            String normalized = value.asText().trim().toLowerCase();
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_BOOLEAN_INVALID",
                "POS boolean field is invalid: " + field);
    }

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_IDENTIFIER_INVALID",
                    "POS identifier must be numeric: " + field);
        }
        try {
            int intValue = Integer.parseInt(value.asText());
            return intValue > 0 ? intValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_IDENTIFIER_INVALID",
                    "POS identifier must be numeric: " + field);
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_IDENTIFIER_INVALID",
                    "POS identifier must be numeric: " + field);
        }
        try {
            long longValue = Long.parseLong(value.asText());
            return longValue > 0 ? longValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_POS_IDENTIFIER_INVALID",
                    "POS identifier must be numeric: " + field);
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

    private String normalizePaymentMethod(String value) {
        String method = value == null ? "cash" : value.trim().toLowerCase();
        if ("direct".equals(method) || "dirict".equals(method)) {
            return "cash";
        }
        if ("credit".equals(method)) {
            return "receivable";
        }
        if ("card".equals(method) || "visa".equals(method) || "mastercard".equals(method)) {
            return "card";
        }
        if ("instapay".equals(method) || "wallet".equals(method)) {
            return "wallet";
        }
        return method;
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

    private record CogsLine(BigDecimal amount, Long productId, Long inventoryMovementId) {
    }
}
