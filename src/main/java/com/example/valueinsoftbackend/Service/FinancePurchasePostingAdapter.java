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
public class FinancePurchasePostingAdapter implements FinancePostingAdapter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(8);
    private static final Set<String> PURCHASE_INVOICE_SOURCE_TYPES = Set.of(
            "purchase",
            "purchase_invoice",
            "supplier_invoice",
            "goods_receipt",
            "stock_receipt");

    private final DbFinanceSetup dbFinanceSetup;
    private final DbFinanceJournal dbFinanceJournal;
    private final ObjectMapper objectMapper;

    public FinancePurchasePostingAdapter(DbFinanceSetup dbFinanceSetup,
            DbFinanceJournal dbFinanceJournal,
            ObjectMapper objectMapper) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbFinanceJournal = dbFinanceJournal;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceModule) {
        return "purchase".equals(sourceModule);
    }

    @Override
    public UUID post(FinancePostingRequestItem request) {
        if (!PURCHASE_INVOICE_SOURCE_TYPES.contains(request.getSourceType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_SOURCE_TYPE_UNSUPPORTED",
                    "Purchase posting adapter currently supports purchase invoice and goods receipt source types only");
        }

        JsonNode payload = parsePayload(request);
        String currencyCode = text(payload, "currencyCode", "EGP");
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }

        Integer supplierId = integerValue(payload, "supplierId");
        if (supplierId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_SUPPLIER_REQUIRED",
                    "Purchase posting requires supplierId");
        }

        BigDecimal inventoryAmount = firstOptionalAmount(payload,
                "inventoryAmount",
                "goodsAmount",
                "subtotalAmount",
                "purchaseAmount");
        List<PurchaseInventoryLine> inventoryLines = inventoryLinesFromItems(payload);
        BigDecimal itemInventoryAmount = ZERO;
        for (PurchaseInventoryLine inventoryLine : inventoryLines) {
            itemInventoryAmount = itemInventoryAmount.add(inventoryLine.amount());
        }

        if (inventoryAmount.compareTo(ZERO) > 0
                && itemInventoryAmount.compareTo(ZERO) > 0
                && inventoryAmount.compareTo(itemInventoryAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_INVENTORY_TOTAL_MISMATCH",
                    "Purchase inventory amount must match item total when both are provided");
        }
        if (itemInventoryAmount.compareTo(ZERO) == 0 && inventoryAmount.compareTo(ZERO) > 0) {
            inventoryLines = List.of(new PurchaseInventoryLine(
                    inventoryAmount,
                    longValue(payload, "productId"),
                    longValue(payload, "inventoryMovementId")));
            itemInventoryAmount = inventoryAmount;
        }
        if (itemInventoryAmount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_INVENTORY_AMOUNT_REQUIRED",
                    "Purchase posting requires a positive inventory amount");
        }

        BigDecimal taxAmount = firstOptionalAmount(payload, "taxAmount", "inputTaxAmount", "vatAmount");
        BigDecimal grossAmount = firstOptionalAmount(payload, "grossAmount", "invoiceAmount", "totalAmount");
        if (grossAmount.compareTo(ZERO) == 0) {
            grossAmount = itemInventoryAmount.add(taxAmount).setScale(4, RoundingMode.HALF_UP);
        }
        if (grossAmount.compareTo(itemInventoryAmount.add(taxAmount).setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_TOTAL_MISMATCH",
                    "Purchase gross amount must equal inventory amount plus tax amount");
        }

        BigDecimal paidAmount = firstOptionalAmount(payload, "paidAmount", "paymentAmount", "amountPaid");
        if (paidAmount.compareTo(grossAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_PAYMENT_AMOUNT_INVALID",
                    "Purchase paid amount cannot exceed gross amount");
        }
        BigDecimal payableAmount = grossAmount.subtract(paidAmount).setScale(4, RoundingMode.HALF_UP);

        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        FinanceAccountMappingItem inventoryMapping = resolveMapping(request, "purchase.inventory");
        for (PurchaseInventoryLine inventoryLine : inventoryLines) {
            lines.add(debitLine(
                    inventoryMapping,
                    request,
                    inventoryLine.amount(),
                    "Purchase inventory receipt",
                    supplierId,
                    inventoryLine.productId(),
                    inventoryLine.inventoryMovementId(),
                    null));
        }
        if (taxAmount.compareTo(ZERO) > 0) {
            lines.add(debitLine(
                    resolveMapping(request, "purchase.input_vat"),
                    request,
                    taxAmount,
                    "Purchase input VAT",
                    supplierId,
                    null,
                    null,
                    null));
        }
        if (paidAmount.compareTo(ZERO) > 0) {
            String paymentMethod = normalizePaymentMethod(text(payload, "paymentMethod", "cash"));
            lines.add(creditLine(
                    resolveMapping(request, "purchase." + paymentMethod),
                    request,
                    paidAmount,
                    "Purchase " + paymentMethod + " payment",
                    supplierId,
                    null,
                    null,
                    text(payload, "paymentId", null)));
        }
        if (payableAmount.compareTo(ZERO) > 0) {
            String payableMappingKey = isGoodsReceipt(request)
                    ? "purchase.grni"
                    : "purchase.payable";
            lines.add(creditLine(
                    resolveMapping(request, payableMappingKey),
                    request,
                    payableAmount,
                    isGoodsReceipt(request) ? "Purchase goods received not invoiced" : "Purchase supplier payable",
                    supplierId,
                    null,
                    null,
                    null));
        }

        BigDecimal totalDebit = totalDebit(lines);
        BigDecimal totalCredit = totalCredit(lines);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_JOURNAL_UNBALANCED",
                    "Purchase posting lines are not balanced");
        }

        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                "purchase.invoice",
                "PI-");
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }

        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        request.getCompanyId(),
                        request.getBranchId(),
                        journalNumber,
                        "purchase",
                        request.getSourceModule(),
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getPostingDate(),
                        request.getFiscalPeriodId(),
                        "Purchase " + request.getSourceId(),
                        currencyCode,
                        ONE,
                        totalDebit,
                        totalCredit,
                        postedBy,
                        lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private List<PurchaseInventoryLine> inventoryLinesFromItems(JsonNode payload) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }

        ArrayList<PurchaseInventoryLine> inventoryLines = new ArrayList<>();
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
            inventoryLines.add(new PurchaseInventoryLine(
                    totalCost,
                    longValue(item, "productId"),
                    firstOptionalLong(item, "inventoryMovementId", "stockLedgerId", "stock_ledger_id")));
        }
        return inventoryLines;
    }

    private FinanceAccountMappingItem resolveMapping(FinancePostingRequestItem request, String mappingKey) {
        FinanceAccountMappingItem mapping = dbFinanceSetup.resolveActiveAccountMapping(
                request.getCompanyId(),
                request.getBranchId(),
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
            Integer supplierId,
            Long productId,
            Long inventoryMovementId,
            String paymentId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                amount,
                ZERO,
                description,
                null,
                supplierId,
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
            Integer supplierId,
            Long productId,
            Long inventoryMovementId,
            String paymentId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                ZERO,
                amount,
                description,
                null,
                supplierId,
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_PAYLOAD_INVALID",
                    "Purchase posting payload is not valid JSON");
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
        return optionalDecimal(node, field, 4);
    }

    private BigDecimal optionalDecimal(JsonNode node, String field, int scale) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return ZERO;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_AMOUNT_INVALID",
                    "Purchase amount must be numeric: " + field);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.asText()).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_AMOUNT_INVALID",
                    "Purchase amount must be numeric: " + field);
        }
        if (amount.compareTo(ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_AMOUNT_INVALID",
                    "Purchase amount cannot be negative: " + field);
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

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_IDENTIFIER_INVALID",
                    "Purchase identifier must be numeric: " + field);
        }
        try {
            int intValue = Integer.parseInt(value.asText());
            return intValue > 0 ? intValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_IDENTIFIER_INVALID",
                    "Purchase identifier must be numeric: " + field);
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_IDENTIFIER_INVALID",
                    "Purchase identifier must be numeric: " + field);
        }
        try {
            long longValue = Long.parseLong(value.asText());
            return longValue > 0 ? longValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PURCHASE_IDENTIFIER_INVALID",
                    "Purchase identifier must be numeric: " + field);
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
        if ("card".equals(method) || "visa".equals(method) || "mastercard".equals(method)) {
            return "card";
        }
        if ("instapay".equals(method) || "wallet".equals(method)) {
            return "wallet";
        }
        if ("bank".equals(method) || "transfer".equals(method) || "bank_transfer".equals(method)) {
            return "bank";
        }
        return method;
    }

    private boolean isGoodsReceipt(FinancePostingRequestItem request) {
        return "goods_receipt".equals(request.getSourceType())
                || "stock_receipt".equals(request.getSourceType());
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

    private record PurchaseInventoryLine(BigDecimal amount, Long productId, Long inventoryMovementId) {
    }
}
