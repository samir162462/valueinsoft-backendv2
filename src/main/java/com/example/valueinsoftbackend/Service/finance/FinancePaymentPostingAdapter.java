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
public class FinancePaymentPostingAdapter implements FinancePostingAdapter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(8);
    private static final Set<String> SETTLEMENT_SOURCE_TYPES = Set.of(
            "settlement",
            "payment_settlement",
            "card_settlement",
            "wallet_settlement",
            "bank_settlement",
            "cash_safe_drop",
            "safe_drop",
            "cash_drawer_close");
    private static final Set<String> CUSTOMER_RECEIPT_SOURCE_TYPES = Set.of(
            "customer_receipt",
            "customer_payment",
            "client_receipt");
    private static final Set<String> SUPPLIER_PAYMENT_SOURCE_TYPES = Set.of(
            "supplier_payment",
            "supplier_receipt",
            "vendor_payment");

    private final DbFinanceSetup dbFinanceSetup;
    private final DbFinanceJournal dbFinanceJournal;
    private final ObjectMapper objectMapper;

    public FinancePaymentPostingAdapter(DbFinanceSetup dbFinanceSetup,
                                        DbFinanceJournal dbFinanceJournal,
                                        ObjectMapper objectMapper) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbFinanceJournal = dbFinanceJournal;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceModule) {
        return "payment".equals(sourceModule);
    }

    @Override
    public UUID post(FinancePostingRequestItem request) {
        if (!SETTLEMENT_SOURCE_TYPES.contains(request.getSourceType())
                && !CUSTOMER_RECEIPT_SOURCE_TYPES.contains(request.getSourceType())
                && !SUPPLIER_PAYMENT_SOURCE_TYPES.contains(request.getSourceType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_SOURCE_TYPE_UNSUPPORTED",
                    "Payment posting adapter currently supports settlement, customer receipt, and supplier payment source types only");
        }

        JsonNode payload = parsePayload(request);
        String currencyCode = text(payload, "currencyCode", "EGP");
        if (!currencyCode.matches("^[A-Z]{3}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CURRENCY_INVALID",
                    "Currency code must use three uppercase letters");
        }

        if (CUSTOMER_RECEIPT_SOURCE_TYPES.contains(request.getSourceType())) {
            return postCustomerReceipt(request, payload, currencyCode);
        }
        if (SUPPLIER_PAYMENT_SOURCE_TYPES.contains(request.getSourceType())) {
            return postSupplierPayment(request, payload, currencyCode);
        }
        return postSettlement(request, payload, currencyCode);
    }

    private UUID postSettlement(FinancePostingRequestItem request, JsonNode payload, String currencyCode) {
        BigDecimal grossAmount = firstOptionalAmount(payload,
                "grossAmount",
                "settlementGrossAmount",
                "clearingAmount",
                "expectedAmount",
                "amount");
        BigDecimal feeAmount = firstOptionalAmount(payload, "feeAmount", "providerFeeAmount", "processingFeeAmount");
        BigDecimal netAmount = firstOptionalAmount(payload,
                "netAmount",
                "settledAmount",
                "depositAmount",
                "bankAmount",
                "cashAmount");
        if (grossAmount.compareTo(ZERO) == 0 && netAmount.compareTo(ZERO) > 0) {
            grossAmount = netAmount.add(feeAmount).setScale(4, RoundingMode.HALF_UP);
        }
        if (netAmount.compareTo(ZERO) == 0 && grossAmount.compareTo(ZERO) > 0) {
            netAmount = grossAmount.subtract(feeAmount).setScale(4, RoundingMode.HALF_UP);
        }
        if (grossAmount.compareTo(ZERO) <= 0 || netAmount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_AMOUNT_REQUIRED",
                    "Payment settlement posting requires positive gross and net amounts");
        }
        if (feeAmount.compareTo(grossAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_FEE_INVALID",
                    "Payment settlement fee cannot exceed gross amount");
        }
        if (grossAmount.compareTo(netAmount.add(feeAmount).setScale(4, RoundingMode.HALF_UP)) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_TOTAL_MISMATCH",
                    "Payment settlement gross amount must equal net amount plus fee amount");
        }

        String settlementMethod = normalizePaymentMethod(text(payload, "settlementMethod", null));
        if (settlementMethod == null) {
            settlementMethod = normalizePaymentMethod(text(payload, "paymentMethod", null));
        }
        if (settlementMethod == null) {
            settlementMethod = methodFromSourceType(request.getSourceType());
        }

        String destination = normalizeDestination(text(payload, "destination", null));
        if (destination == null) {
            destination = normalizeDestination(text(payload, "depositTo", null));
        }
        if (destination == null) {
            destination = isCashSettlement(settlementMethod) ? "safe" : "bank";
        }

        String paymentId = firstText(payload, "paymentId", "settlementId", "providerSettlementId", "providerReference");
        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        lines.add(debitLine(
                resolveMapping(request, destinationMappingKey(destination), null),
                request,
                netAmount,
                settlementDescription(destination),
                paymentId,
                null,
                null));
        if (feeAmount.compareTo(ZERO) > 0) {
            lines.add(debitLine(
                    resolveMapping(request, "payment.fee_expense", null),
                    request,
                    feeAmount,
                    "Payment processing fee",
                    paymentId,
                    null,
                    null));
        }
        lines.add(creditLine(
                resolveMapping(request, clearingMappingKey(settlementMethod), null),
                request,
                grossAmount,
                clearingDescription(settlementMethod),
                paymentId,
                null,
                null));

        return createPostedPaymentJournal(
                request,
                currencyCode,
                "payment.settlement",
                "PM-",
                "payment",
                "Payment settlement " + request.getSourceId(),
                lines);
    }

    private UUID postCustomerReceipt(FinancePostingRequestItem request, JsonNode payload, String currencyCode) {
        Integer customerId = firstIntegerValue(payload, "customerId", "clientId");
        if (customerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CUSTOMER_RECEIPT_CUSTOMER_REQUIRED",
                    "Customer receipt posting requires customerId");
        }

        BigDecimal amount = firstOptionalAmount(payload, "amount", "receiptAmount", "grossAmount", "netAmount");
        if (amount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_CUSTOMER_RECEIPT_AMOUNT_REQUIRED",
                    "Customer receipt posting requires a positive amount");
        }

        String paymentMethod = normalizePaymentMethod(text(payload, "paymentMethod", "cash"));
        String paymentId = firstText(payload, "paymentId", "receiptId", "clientReceiptId");
        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        lines.add(debitLine(
                resolveMapping(request, paymentInstrumentMappingKey(paymentMethod), null),
                request,
                amount,
                "Customer receipt " + paymentMethod,
                paymentId,
                customerId,
                null));
        lines.add(creditLine(
                resolveMapping(request, "pos.receivable", null),
                request,
                amount,
                "Customer receivable settlement",
                paymentId,
                customerId,
                null));

        return createPostedPaymentJournal(
                request,
                currencyCode,
                "payment.customer_receipt",
                "RC-",
                "receipt",
                "Customer receipt " + request.getSourceId(),
                lines);
    }

    private UUID postSupplierPayment(FinancePostingRequestItem request, JsonNode payload, String currencyCode) {
        Integer supplierId = integerValue(payload, "supplierId");
        if (supplierId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_SUPPLIER_PAYMENT_SUPPLIER_REQUIRED",
                    "Supplier payment posting requires supplierId");
        }

        BigDecimal amount = firstOptionalAmount(payload, "amountPaid", "amount", "grossAmount", "netAmount");
        if (amount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_SUPPLIER_PAYMENT_AMOUNT_REQUIRED",
                    "Supplier payment posting requires a positive amount");
        }

        String paymentMethod = normalizePaymentMethod(text(payload, "paymentMethod", "cash"));
        String paymentId = firstText(payload, "paymentId", "receiptId", "supplierReceiptId");
        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        lines.add(debitLine(
                resolveMapping(request, "purchase.payable", supplierId),
                request,
                amount,
                "Supplier payable settlement",
                paymentId,
                null,
                supplierId));
        lines.add(creditLine(
                resolveMapping(request, paymentInstrumentMappingKey(paymentMethod), null),
                request,
                amount,
                "Supplier payment " + paymentMethod,
                paymentId,
                null,
                supplierId));

        return createPostedPaymentJournal(
                request,
                currencyCode,
                "payment.supplier_payment",
                "SP-",
                "supplier_payment",
                "Supplier payment " + request.getSourceId(),
                lines);
    }

    private UUID createPostedPaymentJournal(FinancePostingRequestItem request,
                                            String currencyCode,
                                            String sequenceKey,
                                            String prefix,
                                            String journalType,
                                            String description,
                                            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        BigDecimal totalDebit = totalDebit(lines);
        BigDecimal totalCredit = totalCredit(lines);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_JOURNAL_UNBALANCED",
                    "Payment posting lines are not balanced");
        }

        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                sequenceKey,
                prefix);
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) {
            postedBy = 0;
        }

        UUID journalEntryId = dbFinanceJournal.createPostedSourceJournal(
                new DbFinanceJournal.PostedSourceJournalCommand(
                        request.getCompanyId(),
                        request.getBranchId(),
                        journalNumber,
                        journalType,
                        request.getSourceModule(),
                        request.getSourceType(),
                        request.getSourceId(),
                        request.getPostingDate(),
                        request.getFiscalPeriodId(),
                        description,
                        currencyCode,
                        ONE,
                        totalDebit,
                        totalCredit,
                        postedBy,
                        lines));
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private FinanceAccountMappingItem resolveMapping(FinancePostingRequestItem request, String mappingKey, Integer supplierId) {
        FinanceAccountMappingItem mapping = dbFinanceSetup.resolveActiveAccountMapping(
                request.getCompanyId(),
                request.getBranchId(),
                supplierId,
                mappingKey,
                request.getPostingDate());
        if (mapping == null) {
            throw new ApiException(HttpStatus.CONFLICT, "FINANCE_ACCOUNT_MAPPING_MISSING",
                    "Missing active finance account mapping: " + mappingKey + (supplierId != null ? " for supplier " + supplierId : ""));
        }
        return mapping;
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand debitLine(FinanceAccountMappingItem mapping,
                                                                      FinancePostingRequestItem request,
                                                                      BigDecimal amount,
                                                                      String description,
                                                                      String paymentId,
                                                                      Integer customerId,
                                                                      Integer supplierId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                amount,
                ZERO,
                description,
                customerId,
                supplierId,
                null,
                null,
                paymentId,
                null,
                null);
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand creditLine(FinanceAccountMappingItem mapping,
                                                                       FinancePostingRequestItem request,
                                                                       BigDecimal amount,
                                                                       String description,
                                                                       String paymentId,
                                                                       Integer customerId,
                                                                       Integer supplierId) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                mapping.getAccountId(),
                request.getBranchId(),
                ZERO,
                amount,
                description,
                customerId,
                supplierId,
                null,
                null,
                paymentId,
                null,
                null);
    }

    private JsonNode parsePayload(FinancePostingRequestItem request) {
        try {
            return objectMapper.readTree(request.getRequestPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_PAYLOAD_INVALID",
                    "Payment posting payload is not valid JSON");
        }
    }

    private String clearingMappingKey(String settlementMethod) {
        return switch (settlementMethod) {
            case "card" -> "payment.card_clearing";
            case "wallet" -> "payment.wallet_clearing";
            case "cash" -> "payment.cash_drawer";
            case "bank" -> "payment.bank";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_METHOD_INVALID",
                    "Unsupported payment settlement method");
        };
    }

    private String paymentInstrumentMappingKey(String paymentMethod) {
        return switch (paymentMethod) {
            case "cash" -> "payment.cash_drawer";
            case "bank" -> "payment.bank";
            case "card" -> "payment.card_clearing";
            case "wallet" -> "payment.wallet_clearing";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_METHOD_INVALID",
                    "Unsupported payment method");
        };
    }

    private String destinationMappingKey(String destination) {
        return switch (destination) {
            case "bank" -> "payment.bank";
            case "cash" -> "payment.cash";
            case "safe" -> "payment.cash_safe";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_DESTINATION_INVALID",
                    "Unsupported payment settlement destination");
        };
    }

    private String clearingDescription(String settlementMethod) {
        return switch (settlementMethod) {
            case "card" -> "Card clearing settlement";
            case "wallet" -> "Wallet clearing settlement";
            case "cash" -> "Cash drawer settlement";
            case "bank" -> "Bank clearing settlement";
            default -> "Payment clearing settlement";
        };
    }

    private String settlementDescription(String destination) {
        return switch (destination) {
            case "bank" -> "Settlement deposited to bank";
            case "cash" -> "Settlement received in cash";
            case "safe" -> "Cash moved to safe";
            default -> "Payment settlement received";
        };
    }

    private String normalizePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String method = value.trim().toLowerCase();
        if ("card".equals(method) || "visa".equals(method) || "mastercard".equals(method)) {
            return "card";
        }
        if ("instapay".equals(method) || "wallet".equals(method) || "mobile_wallet".equals(method)) {
            return "wallet";
        }
        if ("cash".equals(method) || "drawer".equals(method) || "cash_drawer".equals(method)) {
            return "cash";
        }
        if ("bank".equals(method) || "bank_transfer".equals(method) || "transfer".equals(method)) {
            return "bank";
        }
        return method;
    }

    private String methodFromSourceType(String sourceType) {
        if ("card_settlement".equals(sourceType) || "bank_settlement".equals(sourceType)
                || "settlement".equals(sourceType) || "payment_settlement".equals(sourceType)) {
            return "card";
        }
        if ("wallet_settlement".equals(sourceType)) {
            return "wallet";
        }
        if ("cash_safe_drop".equals(sourceType) || "safe_drop".equals(sourceType)
                || "cash_drawer_close".equals(sourceType)) {
            return "cash";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_METHOD_REQUIRED",
                "Payment settlement posting requires settlementMethod or paymentMethod");
    }

    private String normalizeDestination(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String destination = value.trim().toLowerCase();
        if ("bank".equals(destination) || "bank_account".equals(destination) || "deposit_bank".equals(destination)) {
            return "bank";
        }
        if ("cash".equals(destination) || "drawer".equals(destination)) {
            return "cash";
        }
        if ("safe".equals(destination) || "cash_safe".equals(destination) || "vault".equals(destination)) {
            return "safe";
        }
        return destination;
    }

    private boolean isCashSettlement(String settlementMethod) {
        return "cash".equals(settlementMethod);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, null);
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_IDENTIFIER_INVALID",
                    "Payment identifier must be numeric: " + field);
        }
        try {
            int intValue = Integer.parseInt(value.asText());
            return intValue > 0 ? intValue : null;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_IDENTIFIER_INVALID",
                    "Payment identifier must be numeric: " + field);
        }
    }

    private Integer firstIntegerValue(JsonNode node, String... fields) {
        for (String field : fields) {
            Integer value = integerValue(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
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
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return ZERO;
        }
        if (!value.isNumber() && !value.isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_AMOUNT_INVALID",
                    "Payment amount must be numeric: " + field);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(value.asText()).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_AMOUNT_INVALID",
                    "Payment amount must be numeric: " + field);
        }
        if (amount.compareTo(ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_PAYMENT_AMOUNT_INVALID",
                    "Payment amount cannot be negative: " + field);
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
}
