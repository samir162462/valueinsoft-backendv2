package com.example.valueinsoftbackend.Service.client;

import com.example.valueinsoftbackend.DatabaseRequests.DbClientTradeIn;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Request.ClientTradeInPaymentRequest;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-as-seller (trade-in) subledger operations: products a client sold to
 * the shop, the payable owed to them, and idempotent payments that settle it.
 * Finance journals are never written here; a posting request is enqueued and
 * the Finance module creates the balanced double entry.
 */
@Service
public class ClientTradeInService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DbClientTradeIn dbClientTradeIn;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public ClientTradeInService(DbClientTradeIn dbClientTradeIn,
                                FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbClientTradeIn = dbClientTradeIn;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public Map<String, Object> getSummary(int companyId, int clientId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        DbClientTradeIn.TradeInSummary summary = dbClientTradeIn.summarize(companyId, clientId);
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        body.put("receiptCount", summary.receiptCount());
        body.put("totalAmount", money(summary.totalAmount()));
        body.put("paidAmount", money(summary.paidAmount()));
        body.put("remainingAmount", money(summary.remainingAmount()));
        return body;
    }

    public Map<String, Object> listTradeIns(int companyId, int clientId, int page, int size) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        int safeSize = clampPageSize(size);
        int safePage = Math.max(page, 0);
        long total = dbClientTradeIn.countTradeIns(companyId, clientId);
        List<DbClientTradeIn.TradeInReceiptRow> rows =
                dbClientTradeIn.listTradeIns(companyId, clientId, safePage * safeSize, safeSize);
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("page", safePage);
        body.put("size", safeSize);
        body.put("totalElements", total);
        body.put("totalPages", total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        body.put("items", rows);
        return body;
    }

    public Map<String, Object> listPayments(int companyId, int clientId, int page, int size) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        int safeSize = clampPageSize(size);
        int safePage = Math.max(page, 0);
        long total = dbClientTradeIn.countPayments(companyId, clientId);
        List<DbClientTradeIn.PaymentRow> rows =
                dbClientTradeIn.listPayments(companyId, clientId, safePage * safeSize, safeSize);
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("page", safePage);
        body.put("size", safeSize);
        body.put("totalElements", total);
        body.put("totalPages", total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        body.put("items", rows);
        return body;
    }

    /**
     * Statement: receipts (credit to the client) and payments (debit) merged,
     * newest first, with the current outstanding balance from the subledger.
     */
    public Map<String, Object> getStatement(int companyId, int clientId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(clientId, "clientId");
        DbClientTradeIn.TradeInSummary summary = dbClientTradeIn.summarize(companyId, clientId);
        List<DbClientTradeIn.TradeInReceiptRow> receipts = dbClientTradeIn.listTradeIns(companyId, clientId, 0, 500);
        List<DbClientTradeIn.PaymentRow> payments = dbClientTradeIn.listPayments(companyId, clientId, 0, 500);

        ArrayList<Map<String, Object>> entries = new ArrayList<>();
        for (DbClientTradeIn.TradeInReceiptRow receipt : receipts) {
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("entryType", "RECEIPT");
            entry.put("reference", receipt.receiptReference());
            entry.put("tradeInReceiptId", receipt.tradeInReceiptId());
            entry.put("stockLedgerId", receipt.stockLedgerId());
            entry.put("productName", receipt.productName());
            entry.put("conditionCode", receipt.conditionCode());
            entry.put("amount", money(receipt.totalAmount()));
            entry.put("paymentStatus", receipt.paymentStatus());
            entry.put("status", receipt.status());
            entry.put("time", receipt.createdAt());
            entries.add(entry);
        }
        for (DbClientTradeIn.PaymentRow payment : payments) {
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("entryType", "PAYMENT");
            entry.put("reference", "client-tradein-payment-" + payment.paymentId());
            entry.put("paymentId", payment.paymentId());
            entry.put("paymentMethod", payment.paymentMethod());
            entry.put("amount", money(payment.amount()));
            entry.put("status", payment.status());
            entry.put("time", payment.createdAt());
            entries.add(entry);
        }
        entries.sort((a, b) -> {
            Timestamp timeA = (Timestamp) a.get("time");
            Timestamp timeB = (Timestamp) b.get("time");
            return timeB.compareTo(timeA);
        });

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        body.put("totalAmount", money(summary.totalAmount()));
        body.put("paidAmount", money(summary.paidAmount()));
        body.put("remainingAmount", money(summary.remainingAmount()));
        body.put("entries", entries);
        return body;
    }

    /**
     * Idempotent payment to a client. Allocates FIFO against open trade-in
     * receipts, keeps the subledger arithmetic exact, and enqueues the finance
     * posting request. Retries with the same key replay the stored outcome;
     * the same key with a different payload fails with 409.
     */
    @Transactional
    public Map<String, Object> payClient(int companyId, String actorName, ClientTradeInPaymentRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        BigDecimal amount = money(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_INVALID", "amount must be greater than zero");
        }
        String paymentMethod = request.getPaymentMethod() == null ? "" : request.getPaymentMethod().trim();
        if (paymentMethod.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_REQUIRED", "paymentMethod is required");
        }
        String idempotencyKey = request.getIdempotencyKey() == null ? "" : request.getIdempotencyKey().trim();
        if (idempotencyKey.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "idempotencyKey is required");
        }
        String requestHash = hashPayment(companyId, request, amount, paymentMethod);

        DbClientTradeIn.PaymentRow existing = dbClientTradeIn.findPaymentByIdempotencyKey(companyId, idempotencyKey).orElse(null);
        if (existing != null) {
            return replayOrConflict(companyId, existing, requestHash);
        }

        DbClientTradeIn.ClientRow client = dbClientTradeIn.findClientForUpdate(companyId, request.getClientId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", "Client was not found for this tenant"));
        if (!client.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "CLIENT_NOT_ACTIVE", "Archived clients cannot receive payments");
        }

        List<DbClientTradeIn.TradeInReceiptRow> openReceipts =
                dbClientTradeIn.listOpenReceiptsForUpdate(companyId, request.getClientId());
        BigDecimal outstanding = BigDecimal.ZERO.setScale(4);
        for (DbClientTradeIn.TradeInReceiptRow receipt : openReceipts) {
            outstanding = outstanding.add(money(receipt.remainingAmount()));
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_EXCEEDS_PAYABLE",
                    "Payment amount exceeds the outstanding trade-in payable (" + outstanding.toPlainString() + ")");
        }

        long paymentId;
        try {
            paymentId = dbClientTradeIn.insertPayment(
                    companyId,
                    request.getBranchId(),
                    request.getClientId(),
                    amount,
                    paymentMethod,
                    blankToNull(request.getNotes()),
                    idempotencyKey,
                    requestHash,
                    actorName);
        } catch (DuplicateKeyException exception) {
            DbClientTradeIn.PaymentRow concurrent = dbClientTradeIn.findPaymentByIdempotencyKey(companyId, idempotencyKey)
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_IDEMPOTENCY_STATE_MISSING",
                            "Payment idempotency state could not be loaded"));
            return replayOrConflict(companyId, concurrent, requestHash);
        }

        ArrayList<Map<String, Object>> allocations = new ArrayList<>();
        BigDecimal remainingToAllocate = amount;
        for (DbClientTradeIn.TradeInReceiptRow receipt : openReceipts) {
            if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal allocatable = money(receipt.remainingAmount()).min(remainingToAllocate);
            if (allocatable.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int updated = dbClientTradeIn.applyPaymentToReceipt(companyId, receipt.tradeInReceiptId(), allocatable, actorName);
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALLOCATION_CONFLICT",
                        "Trade-in receipt balance changed while allocating the payment");
            }
            dbClientTradeIn.insertAllocation(companyId, paymentId, receipt.tradeInReceiptId(), allocatable);
            LinkedHashMap<String, Object> allocation = new LinkedHashMap<>();
            allocation.put("tradeInReceiptId", receipt.tradeInReceiptId());
            allocation.put("amount", allocatable);
            allocations.add(allocation);
            remainingToAllocate = remainingToAllocate.subtract(allocatable).setScale(4, RoundingMode.HALF_UP);
        }
        if (remainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALLOCATION_INCOMPLETE",
                    "Payment could not be fully allocated against open receipts");
        }

        FinancePostingRequestItem financeRequest = financeOperationalPostingService.enqueueClientTradeInPayment(
                companyId,
                request.getBranchId(),
                request.getClientId(),
                paymentId,
                amount,
                paymentMethod,
                new Timestamp(System.currentTimeMillis()),
                actorName);
        if (financeRequest != null && financeRequest.getPostingRequestId() != null) {
            dbClientTradeIn.updatePaymentPostingRequest(companyId, paymentId, financeRequest.getPostingRequestId());
        }

        DbClientTradeIn.TradeInSummary summary = dbClientTradeIn.summarize(companyId, request.getClientId());
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("paymentId", paymentId);
        body.put("idempotentReplay", false);
        body.put("clientId", request.getClientId());
        body.put("amount", amount);
        body.put("paymentMethod", paymentMethod);
        body.put("allocations", allocations);
        body.put("remainingPayable", money(summary.remainingAmount()));
        body.put("financeStatus", financeRequest == null ? "SKIPPED" : financeRequest.getStatus());
        return body;
    }

    private Map<String, Object> replayOrConflict(int companyId, DbClientTradeIn.PaymentRow existing, String requestHash) {
        if (existing.requestHash() != null && !existing.requestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_PAYLOAD_CONFLICT",
                    "The same idempotency key was already used with a different payment payload");
        }
        DbClientTradeIn.TradeInSummary summary = dbClientTradeIn.summarize(companyId, existing.clientId());
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("paymentId", existing.paymentId());
        body.put("idempotentReplay", true);
        body.put("clientId", existing.clientId());
        body.put("amount", money(existing.amount()));
        body.put("paymentMethod", existing.paymentMethod());
        body.put("allocations", List.of());
        body.put("remainingPayable", money(summary.remainingAmount()));
        body.put("financeStatus", existing.postingRequestId() == null ? "UNKNOWN" : "ENQUEUED");
        return body;
    }

    private String hashPayment(int companyId, ClientTradeInPaymentRequest request, BigDecimal amount, String paymentMethod) {
        String canonical = companyId + "|" + request.getBranchId() + "|" + request.getClientId() + "|"
                + amount.toPlainString() + "|" + paymentMethod.toLowerCase(Locale.ROOT) + "|"
                + (blankToNull(request.getNotes()) == null ? "" : request.getNotes().trim());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hashed) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_HASH_FAILED", "Payment request could not be hashed");
        }
    }

    private int clampPageSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4) : value.setScale(4, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
