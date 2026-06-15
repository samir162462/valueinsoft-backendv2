package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceJournal;
import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinanceAccountItem;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class FinanceExpensePostingAdapter implements FinancePostingAdapter {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(8);

    private final DbFinanceSetup dbFinanceSetup;
    private final DbFinanceJournal dbFinanceJournal;
    private final ObjectMapper objectMapper;

    public FinanceExpensePostingAdapter(DbFinanceSetup dbFinanceSetup,
                                       DbFinanceJournal dbFinanceJournal,
                                       ObjectMapper objectMapper) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.dbFinanceJournal = dbFinanceJournal;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceModule) {
        return "expense".equals(sourceModule);
    }

    @Override
    public UUID post(FinancePostingRequestItem request) {
        JsonNode payload = parsePayload(request);
        String currencyCode = "EGP"; // Defaulting to EGP for now as per other adapters
        
        BigDecimal amount = optionalAmount(payload, "amount");
        if (amount.compareTo(ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_EXPENSE_AMOUNT_REQUIRED",
                    "Expense posting requires a positive amount");
        }

        UUID expenseAccountId = uuid(payload, "expenseAccountId");
        UUID paymentAccountId = uuid(payload, "paymentAccountId");
        String description = text(payload, "description", "Operational Expense");

        if (expenseAccountId == null || paymentAccountId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_EXPENSE_ACCOUNTS_REQUIRED",
                    "Expense posting requires both expense and payment accounts");
        }

        ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines = new ArrayList<>();
        
        // Debit: Expense Account
        lines.add(debitLine(
                expenseAccountId,
                request.getBranchId(),
                amount,
                description));
        
        // Credit: Payment Account (Cash/Bank)
        lines.add(creditLine(
                paymentAccountId,
                request.getBranchId(),
                amount,
                description));

        return createPostedExpenseJournal(
                request,
                currencyCode,
                "expense.operational",
                "EX-",
                "expense",
                description + " (" + request.getSourceId() + ")",
                lines);
    }

    private UUID createPostedExpenseJournal(FinancePostingRequestItem request,
                                            String currencyCode,
                                            String sequenceKey,
                                            String prefix,
                                            String journalType,
                                            String description,
                                            ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        String journalNumber = dbFinanceJournal.allocateSourceJournalNumber(
                request.getCompanyId(),
                sequenceKey,
                prefix);
        
        Integer postedBy = request.getUpdatedBy() == null ? request.getCreatedBy() : request.getUpdatedBy();
        if (postedBy == null) postedBy = 0;

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
                        totalAmount(lines),
                        totalAmount(lines),
                        postedBy,
                        lines));
        
        dbFinanceJournal.applyPostedJournalToAccountBalances(request.getCompanyId(), journalEntryId, postedBy);
        return journalEntryId;
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand debitLine(UUID accountId,
                                                                      Integer branchId,
                                                                      BigDecimal amount,
                                                                      String description) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                accountId, branchId, amount, ZERO, description, null, null, null, null, null, null, null);
    }

    private DbFinanceJournal.PostedSourceJournalLineCommand creditLine(UUID accountId,
                                                                       Integer branchId,
                                                                       BigDecimal amount,
                                                                       String description) {
        return new DbFinanceJournal.PostedSourceJournalLineCommand(
                accountId, branchId, ZERO, amount, description, null, null, null, null, null, null, null);
    }

    private BigDecimal totalAmount(ArrayList<DbFinanceJournal.PostedSourceJournalLineCommand> lines) {
        return lines.get(0).debitAmount(); // Balanced entry, so first debit works
    }

    private JsonNode parsePayload(FinancePostingRequestItem request) {
        try {
            return objectMapper.readTree(request.getRequestPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FINANCE_EXPENSE_PAYLOAD_INVALID",
                    "Expense posting payload is not valid JSON");
        }
    }

    private BigDecimal optionalAmount(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return ZERO;
        try {
            return new BigDecimal(value.asText()).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return ZERO;
        }
    }

    private UUID uuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return defaultValue;
        return value.asText();
    }
}
