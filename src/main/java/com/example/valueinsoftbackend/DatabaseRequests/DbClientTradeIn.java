package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped access to the client trade-in payable subledger:
 * client_tradein_receipt, client_tradein_payment, and allocations.
 * All money columns are NUMERIC(19,4).
 */
@Repository
public class DbClientTradeIn {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbClientTradeIn(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ClientRow> findClientForUpdate(int companyId, int clientId) {
        String sql = """
                SELECT c_id, "clientName", "clientPhone", COALESCE(status, 'ACTIVE') AS status
                FROM %s
                WHERE c_id = :clientId
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.clientTable(companyId));
        List<ClientRow> rows = jdbcTemplate.query(sql, new MapSqlParameterSource().addValue("clientId", clientId),
                (rs, rowNum) -> new ClientRow(
                        rs.getInt("c_id"),
                        rs.getString("clientName"),
                        rs.getString("clientPhone"),
                        rs.getString("status")));
        return rows.stream().findFirst();
    }

    public TradeInSummary summarize(int companyId, int clientId) {
        String sql = """
                SELECT COUNT(*) AS receipt_count,
                       COALESCE(SUM(total_amount), 0) AS total_amount,
                       COALESCE(SUM(paid_amount), 0) AS paid_amount,
                       COALESCE(SUM(remaining_amount), 0) AS remaining_amount
                FROM %s
                WHERE company_id = :companyId
                  AND client_id = :clientId
                  AND status = 'POSTED'
                """.formatted(TenantSqlIdentifiers.clientTradeInReceiptTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("clientId", clientId),
                (rs, rowNum) -> new TradeInSummary(
                        rs.getLong("receipt_count"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("remaining_amount")));
    }

    public long countTradeIns(int companyId, int clientId) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE company_id = :companyId
                  AND client_id = :clientId
                """.formatted(TenantSqlIdentifiers.clientTradeInReceiptTable(companyId));
        Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("clientId", clientId), Long.class);
        return count == null ? 0 : count;
    }

    public List<TradeInReceiptRow> listTradeIns(int companyId, int clientId, int offset, int limit) {
        String sql = """
                SELECT r.tradein_receipt_id,
                       r.branch_id,
                       b."branchName" AS branch_name,
                       r.stock_ledger_id,
                       r.product_id,
                       p.product_name,
                       p.sku,
                       p.barcode,
                       r.receipt_reference,
                       r.quantity,
                       r.condition_code,
                       r.condition_notes,
                       r.unit_cost,
                       r.total_amount,
                       r.paid_amount,
                       r.remaining_amount,
                       r.payment_status,
                       r.payment_method,
                       r.status,
                       r.created_at,
                       (SELECT string_agg(u.serial_number, ', ')
                          FROM %s u
                         WHERE u.company_id = r.company_id
                           AND u.product_id = r.product_id
                           AND u.purchase_reference_id = r.receipt_reference
                           AND u.serial_number IS NOT NULL) AS serial_numbers,
                       (SELECT string_agg(u.imei, ', ')
                          FROM %s u
                         WHERE u.company_id = r.company_id
                           AND u.product_id = r.product_id
                           AND u.purchase_reference_id = r.receipt_reference
                           AND u.imei IS NOT NULL) AS imeis
                FROM %s r
                JOIN %s p ON p.product_id = r.product_id
                LEFT JOIN public."Branch" b ON b."branchId" = r.branch_id
                WHERE r.company_id = :companyId
                  AND r.client_id = :clientId
                ORDER BY r.created_at DESC, r.tradein_receipt_id DESC
                OFFSET :offset LIMIT :limit
                """.formatted(
                TenantSqlIdentifiers.inventoryProductUnitTable(companyId),
                TenantSqlIdentifiers.inventoryProductUnitTable(companyId),
                TenantSqlIdentifiers.clientTradeInReceiptTable(companyId),
                TenantSqlIdentifiers.inventoryProductTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("clientId", clientId)
                        .addValue("offset", offset)
                        .addValue("limit", limit),
                (rs, rowNum) -> new TradeInReceiptRow(
                        rs.getLong("tradein_receipt_id"),
                        rs.getInt("branch_id"),
                        rs.getString("branch_name"),
                        rs.getLong("stock_ledger_id"),
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getString("sku"),
                        rs.getString("barcode"),
                        rs.getString("receipt_reference"),
                        rs.getInt("quantity"),
                        rs.getString("condition_code"),
                        rs.getString("condition_notes"),
                        rs.getBigDecimal("unit_cost"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("remaining_amount"),
                        rs.getString("payment_status"),
                        rs.getString("payment_method"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getString("serial_numbers"),
                        rs.getString("imeis")));
    }

    public List<TradeInReceiptRow> listOpenReceiptsForUpdate(int companyId, int clientId) {
        String sql = """
                SELECT tradein_receipt_id, branch_id, stock_ledger_id, product_id, receipt_reference,
                       quantity, condition_code, condition_notes, unit_cost, total_amount, paid_amount,
                       remaining_amount, payment_status, payment_method, status, created_at
                FROM %s
                WHERE company_id = :companyId
                  AND client_id = :clientId
                  AND status = 'POSTED'
                  AND remaining_amount > 0
                ORDER BY created_at ASC, tradein_receipt_id ASC
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.clientTradeInReceiptTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("clientId", clientId),
                (rs, rowNum) -> new TradeInReceiptRow(
                        rs.getLong("tradein_receipt_id"),
                        rs.getInt("branch_id"),
                        null,
                        rs.getLong("stock_ledger_id"),
                        rs.getLong("product_id"),
                        null,
                        null,
                        null,
                        rs.getString("receipt_reference"),
                        rs.getInt("quantity"),
                        rs.getString("condition_code"),
                        rs.getString("condition_notes"),
                        rs.getBigDecimal("unit_cost"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("remaining_amount"),
                        rs.getString("payment_status"),
                        rs.getString("payment_method"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        null,
                        null));
    }

    public int applyPaymentToReceipt(int companyId, long tradeInReceiptId, BigDecimal allocatedAmount, String actorName) {
        String sql = """
                UPDATE %s
                SET paid_amount = paid_amount + :allocatedAmount,
                    remaining_amount = remaining_amount - :allocatedAmount,
                    payment_status = CASE
                        WHEN remaining_amount - :allocatedAmount <= 0 THEN 'PAID'
                        ELSE 'PARTIALLY_PAID'
                    END,
                    updated_by = :actorName,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE company_id = :companyId
                  AND tradein_receipt_id = :tradeInReceiptId
                  AND remaining_amount >= :allocatedAmount
                """.formatted(TenantSqlIdentifiers.clientTradeInReceiptTable(companyId));
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("tradeInReceiptId", tradeInReceiptId)
                .addValue("allocatedAmount", allocatedAmount)
                .addValue("actorName", actorName));
    }

    public Optional<PaymentRow> findPaymentByIdempotencyKey(int companyId, String idempotencyKey) {
        String sql = """
                SELECT payment_id, company_id, branch_id, client_id, amount, payment_method, notes,
                       idempotency_key, request_hash, status, posting_request_id, created_by, created_at
                FROM %s
                WHERE company_id = :companyId
                  AND idempotency_key = :idempotencyKey
                """.formatted(TenantSqlIdentifiers.clientTradeInPaymentTable(companyId));
        List<PaymentRow> rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("idempotencyKey", idempotencyKey), this::mapPaymentRow);
        return rows.stream().findFirst();
    }

    public long insertPayment(int companyId,
                              int branchId,
                              int clientId,
                              BigDecimal amount,
                              String paymentMethod,
                              String notes,
                              String idempotencyKey,
                              String requestHash,
                              String actorName) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, client_id, amount, payment_method, notes,
                    idempotency_key, request_hash, status, created_by, created_at,
                    updated_by, updated_at, version
                ) VALUES (
                    :companyId, :branchId, :clientId, :amount, :paymentMethod, :notes,
                    :idempotencyKey, :requestHash, 'POSTED', :actorName, CURRENT_TIMESTAMP,
                    :actorName, CURRENT_TIMESTAMP, 0
                )
                RETURNING payment_id
                """.formatted(TenantSqlIdentifiers.clientTradeInPaymentTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("clientId", clientId)
                .addValue("amount", amount)
                .addValue("paymentMethod", paymentMethod)
                .addValue("notes", notes)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("requestHash", requestHash)
                .addValue("actorName", actorName), Long.class);
    }

    public void updatePaymentPostingRequest(int companyId, long paymentId, UUID postingRequestId) {
        String sql = """
                UPDATE %s
                SET posting_request_id = :postingRequestId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE company_id = :companyId
                  AND payment_id = :paymentId
                """.formatted(TenantSqlIdentifiers.clientTradeInPaymentTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("paymentId", paymentId)
                .addValue("postingRequestId", postingRequestId));
    }

    public void insertAllocation(int companyId, long paymentId, long tradeInReceiptId, BigDecimal amount) {
        String sql = """
                INSERT INTO %s (company_id, payment_id, tradein_receipt_id, amount, created_at)
                VALUES (:companyId, :paymentId, :tradeInReceiptId, :amount, CURRENT_TIMESTAMP)
                """.formatted(TenantSqlIdentifiers.clientTradeInPaymentAllocationTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("paymentId", paymentId)
                .addValue("tradeInReceiptId", tradeInReceiptId)
                .addValue("amount", amount));
    }

    public long countPayments(int companyId, int clientId) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE company_id = :companyId
                  AND client_id = :clientId
                """.formatted(TenantSqlIdentifiers.clientTradeInPaymentTable(companyId));
        Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("clientId", clientId), Long.class);
        return count == null ? 0 : count;
    }

    public List<PaymentRow> listPayments(int companyId, int clientId, int offset, int limit) {
        String sql = """
                SELECT payment_id, company_id, branch_id, client_id, amount, payment_method, notes,
                       idempotency_key, request_hash, status, posting_request_id, created_by, created_at
                FROM %s
                WHERE company_id = :companyId
                  AND client_id = :clientId
                ORDER BY created_at DESC, payment_id DESC
                OFFSET :offset LIMIT :limit
                """.formatted(TenantSqlIdentifiers.clientTradeInPaymentTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("clientId", clientId)
                .addValue("offset", offset)
                .addValue("limit", limit), this::mapPaymentRow);
    }

    private PaymentRow mapPaymentRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new PaymentRow(
                rs.getLong("payment_id"),
                rs.getInt("branch_id"),
                rs.getInt("client_id"),
                rs.getBigDecimal("amount"),
                rs.getString("payment_method"),
                rs.getString("notes"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("status"),
                rs.getObject("posting_request_id", UUID.class),
                rs.getString("created_by"),
                rs.getTimestamp("created_at"));
    }

    public record ClientRow(int clientId, String clientName, String clientPhone, String status) {
        public boolean isActive() {
            return status == null || "ACTIVE".equalsIgnoreCase(status.trim());
        }
    }

    public record TradeInSummary(long receiptCount, BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal remainingAmount) {}

    public record TradeInReceiptRow(long tradeInReceiptId,
                                    int branchId,
                                    String branchName,
                                    long stockLedgerId,
                                    long productId,
                                    String productName,
                                    String sku,
                                    String barcode,
                                    String receiptReference,
                                    int quantity,
                                    String conditionCode,
                                    String conditionNotes,
                                    BigDecimal unitCost,
                                    BigDecimal totalAmount,
                                    BigDecimal paidAmount,
                                    BigDecimal remainingAmount,
                                    String paymentStatus,
                                    String paymentMethod,
                                    String status,
                                    Timestamp createdAt,
                                    String serialNumbers,
                                    String imeis) {}

    public record PaymentRow(long paymentId,
                             int branchId,
                             int clientId,
                             BigDecimal amount,
                             String paymentMethod,
                             String notes,
                             String idempotencyKey,
                             String requestHash,
                             String status,
                             UUID postingRequestId,
                             String createdBy,
                             Timestamp createdAt) {}
}
