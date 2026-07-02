package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Billing.BillingBalanceSettlementSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountBalanceResponse;
import com.example.valueinsoftbackend.Model.Billing.BillingAccountLedgerItem;
import com.example.valueinsoftbackend.Model.Billing.BillingOverdueInvoiceCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoiceMutationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoicePaymentContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptSnapshot;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAllocationReversalCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingProviderCheckoutOutboxItem;
import com.example.valueinsoftbackend.Model.Billing.BillingRenewalCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingInvoiceRetryCandidate;
import com.example.valueinsoftbackend.Model.Billing.BranchBillingCheckoutCandidate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DbBillingWriteModels {

    private static final RowMapper<BillingRenewalCandidate> RENEWAL_CANDIDATE_ROW_MAPPER = (rs, rowNum) ->
            new BillingRenewalCandidate(
                    rs.getLong("billing_account_id"),
                    rs.getLong("previous_branch_subscription_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("currency_code"),
                    rs.getString("price_code"),
                    rs.getString("billing_interval"),
                    rs.getBigDecimal("unit_amount"),
                    rs.getDate("current_period_start"),
                    rs.getDate("current_period_end")
            );

    private static final RowMapper<BillingOverdueInvoiceCandidate> OVERDUE_INVOICE_ROW_MAPPER = (rs, rowNum) ->
            new BillingOverdueInvoiceCandidate(
                    rs.getLong("billing_invoice_id"),
                    rs.getLong("billing_account_id"),
                    rs.getLong("branch_subscription_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("provider_code"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("total_amount"),
                    rs.getBigDecimal("due_amount"),
                    rs.getTimestamp("due_at"),
                    rs.getInt("existing_dunning_attempts")
            );

    private static final RowMapper<BillingInvoiceRetryCandidate> INVOICE_RETRY_CANDIDATE_ROW_MAPPER = (rs, rowNum) ->
            new BillingInvoiceRetryCandidate(
                    rs.getLong("billing_invoice_id"),
                    rs.getLong("branch_subscription_id"),
                    rs.getLong("billing_account_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getInt("branch_id"),
                    rs.getString("provider_code"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("due_amount"),
                    rs.getString("invoice_status"),
                    rs.getInt("attempt_count"),
                    rs.getTimestamp("latest_attempt_at")
            );

    private static final RowMapper<BranchBillingCheckoutCandidate> BRANCH_CHECKOUT_CANDIDATE_ROW_MAPPER = (rs, rowNum) ->
            new BranchBillingCheckoutCandidate(
                    rs.getLong("billing_invoice_id"),
                    rs.getLong("branch_subscription_id"),
                    rs.getLong("billing_account_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getInt("branch_id"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("due_amount"),
                    rs.getString("invoice_status"),
                    rs.getString("latest_attempt_status"),
                    rs.getString("latest_external_order_id")
            );

    private static final RowMapper<BillingInvoicePaymentContext> INVOICE_PAYMENT_CONTEXT_ROW_MAPPER = (rs, rowNum) ->
            new BillingInvoicePaymentContext(
                    rs.getLong("billing_invoice_id"),
                    rs.getLong("billing_account_id"),
                    (Integer) rs.getObject("tenant_id"),
                    rs.getInt("company_id"),
                    (Long) rs.getObject("branch_subscription_id"),
                    (Integer) rs.getObject("branch_id"),
                    rs.getString("invoice_status"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("total_amount"),
                    rs.getBigDecimal("paid_amount"),
                    rs.getBigDecimal("due_amount"),
                    rs.getBigDecimal("available_balance")
            );

    private static final RowMapper<BillingBalanceSettlementSnapshot> BALANCE_SETTLEMENT_SNAPSHOT_ROW_MAPPER = (rs, rowNum) ->
            new BillingBalanceSettlementSnapshot(
                    rs.getLong("billing_payment_id"),
                    (Long) rs.getObject("billing_payment_allocation_id"),
                    (Long) rs.getObject("billing_account_ledger_id"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency_code")
            );

    private static final RowMapper<BillingPaymentAttemptSnapshot> PAYMENT_ATTEMPT_SNAPSHOT_ROW_MAPPER = (rs, rowNum) ->
            new BillingPaymentAttemptSnapshot(
                    rs.getLong("billing_payment_attempt_id"),
                    rs.getString("provider_code"),
                    rs.getString("external_order_id"),
                    rs.getString("status"),
                    rs.getBigDecimal("requested_amount"),
                    rs.getString("currency_code"),
                    rs.getString("checkout_url")
            );

    private static final RowMapper<BillingPaymentAllocationReversalCandidate> PAYMENT_ALLOCATION_REVERSAL_ROW_MAPPER = (rs, rowNum) ->
            new BillingPaymentAllocationReversalCandidate(
                    rs.getLong("billing_payment_id"),
                    rs.getLong("billing_payment_allocation_id"),
                    rs.getLong("billing_invoice_id"),
                    rs.getLong("billing_account_id"),
                    rs.getString("payment_source"),
                    rs.getString("provider_code"),
                    rs.getString("provider_reference"),
                    rs.getBigDecimal("allocated_amount"),
                    rs.getBigDecimal("reversible_amount"),
                    rs.getString("currency_code")
            );

    private static final RowMapper<BillingProviderCheckoutOutboxItem> PROVIDER_CHECKOUT_OUTBOX_ROW_MAPPER = (rs, rowNum) ->
            new BillingProviderCheckoutOutboxItem(
                    rs.getLong("checkout_outbox_id"),
                    rs.getLong("billing_payment_attempt_id"),
                    rs.getLong("billing_invoice_id"),
                    rs.getInt("company_id"),
                    (Integer) rs.getObject("branch_id"),
                    rs.getString("provider_code"),
                    rs.getString("operation_type"),
                    rs.getString("idempotency_key"),
                    rs.getBigDecimal("requested_amount"),
                    rs.getString("currency_code"),
                    rs.getString("checkout_reference"),
                    rs.getString("request_payload_json"),
                    rs.getInt("attempt_count")
            );

    private static final RowMapper<BillingAccountBalanceResponse> BILLING_ACCOUNT_BALANCE_ROW_MAPPER = (rs, rowNum) ->
            new BillingAccountBalanceResponse(
                    rs.getInt("company_id"),
                    rs.getLong("billing_account_id"),
                    rs.getString("currency_code"),
                    rs.getBigDecimal("available_balance"),
                    rs.getString("status"),
                    rs.getLong("version"),
                    rs.getTimestamp("updated_at")
            );

    private static final RowMapper<BillingAccountLedgerItem> BILLING_ACCOUNT_LEDGER_ROW_MAPPER = (rs, rowNum) ->
            new BillingAccountLedgerItem(
                    rs.getLong("billing_account_ledger_id"),
                    rs.getLong("billing_account_id"),
                    rs.getInt("company_id"),
                    rs.getString("transaction_type"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency_code"),
                    rs.getString("direction"),
                    rs.getBigDecimal("balance_before"),
                    rs.getBigDecimal("balance_after"),
                    rs.getString("reference_type"),
                    rs.getString("reference_id"),
                    rs.getString("funding_source"),
                    rs.getString("credit_reason"),
                    rs.getString("approval_status"),
                    rs.getString("description"),
                    rs.getTimestamp("created_at")
            );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbBillingWriteModels(JdbcTemplate jdbcTemplate,
                                NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Long findBillingAccountIdByCompanyIdAndCurrency(int companyId, String currencyCode) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT billing_account_id FROM public.billing_accounts WHERE company_id = ? AND UPPER(currency_code) = UPPER(?)",
                (rs, rowNum) -> rs.getLong("billing_account_id"),
                companyId,
                currencyCode
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long createBillingAccount(Integer tenantId,
                                     int companyId,
                                     String accountCode,
                                     String currencyCode,
                                     String billToName,
                                     String metadataJson) {
        String sql = "INSERT INTO public.billing_accounts " +
                "(tenant_id, company_id, account_code, currency_code, bill_to_name, metadata_json) " +
                "VALUES (:tenantId, :companyId, :accountCode, :currencyCode, :billToName, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("companyId", companyId)
                        .addValue("accountCode", accountCode)
                        .addValue("currencyCode", currencyCode)
                        .addValue("billToName", billToName)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"billing_account_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public BillingAccountBalanceResponse findBillingAccountBalance(int companyId, String currencyCode) {
        return findBillingAccountBalance(companyId, currencyCode, false);
    }

    public BillingAccountBalanceResponse lockBillingAccountBalance(int companyId, String currencyCode) {
        return findBillingAccountBalance(companyId, currencyCode, true);
    }

    private BillingAccountBalanceResponse findBillingAccountBalance(int companyId, String currencyCode, boolean lockRow) {
        String sql = "SELECT company_id, billing_account_id, currency_code, available_balance, status, version, updated_at " +
                "FROM public.billing_accounts " +
                "WHERE company_id = :companyId AND UPPER(currency_code) = UPPER(:currencyCode)";
        if (lockRow) {
            sql = sql + " FOR UPDATE";
        }

        List<BillingAccountBalanceResponse> items = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("currencyCode", currencyCode),
                BILLING_ACCOUNT_BALANCE_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public List<BillingAccountLedgerItem> findBillingAccountLedger(int companyId,
                                                                   String currencyCode,
                                                                   String transactionType,
                                                                   int limit,
                                                                   int offset) {
        String sql = "SELECT billing_account_ledger_id, billing_account_id, company_id, transaction_type, amount, currency_code, " +
                "direction, balance_before, balance_after, reference_type, reference_id, funding_source, credit_reason, " +
                "approval_status, description, created_at " +
                "FROM public.billing_account_ledger " +
                "WHERE company_id = :companyId AND UPPER(currency_code) = UPPER(:currencyCode) " +
                "AND (:transactionType IS NULL OR transaction_type = :transactionType) " +
                "ORDER BY created_at DESC, billing_account_ledger_id DESC " +
                "LIMIT :limit OFFSET :offset";
        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("currencyCode", currencyCode)
                        .addValue("transactionType", transactionType)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                BILLING_ACCOUNT_LEDGER_ROW_MAPPER
        );
    }

    public BillingAccountLedgerItem findBillingAccountLedgerByIdempotencyKey(int companyId, String idempotencyKey) {
        List<BillingAccountLedgerItem> items = namedParameterJdbcTemplate.query(
                "SELECT billing_account_ledger_id, billing_account_id, company_id, transaction_type, amount, currency_code, " +
                        "direction, balance_before, balance_after, reference_type, reference_id, funding_source, credit_reason, " +
                        "approval_status, description, created_at " +
                        "FROM public.billing_account_ledger " +
                        "WHERE company_id = :companyId AND idempotency_key = :idempotencyKey " +
                        "ORDER BY billing_account_ledger_id DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("idempotencyKey", idempotencyKey),
                BILLING_ACCOUNT_LEDGER_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public BillingInvoicePaymentContext findInvoicePaymentContext(long billingInvoiceId) {
        return findInvoicePaymentContext(billingInvoiceId, false);
    }

    public BillingInvoicePaymentContext lockInvoicePaymentContext(long billingInvoiceId) {
        return findInvoicePaymentContext(billingInvoiceId, true);
    }

    private BillingInvoicePaymentContext findInvoicePaymentContext(long billingInvoiceId, boolean lockRows) {
        String sql = "SELECT bi.billing_invoice_id, bi.billing_account_id, ba.tenant_id, ba.company_id, " +
                "bs.branch_subscription_id, bs.branch_id, bi.status AS invoice_status, bi.currency_code, " +
                "COALESCE(bi.total_amount, 0) AS total_amount, " +
                "COALESCE(bi.paid_amount, GREATEST(COALESCE(bi.total_amount, 0) - COALESCE(bi.due_amount, 0), 0)) AS paid_amount, " +
                "COALESCE(bi.due_amount, 0) AS due_amount, COALESCE(ba.available_balance, 0) AS available_balance " +
                "FROM public.billing_invoices bi " +
                "JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                "LEFT JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                "WHERE bi.billing_invoice_id = :billingInvoiceId";
        if (lockRows) {
            sql = sql + " FOR UPDATE OF bi, ba";
        }

        List<BillingInvoicePaymentContext> items = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("billingInvoiceId", billingInvoiceId),
                INVOICE_PAYMENT_CONTEXT_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public int updateBillingAccountAvailableBalance(long billingAccountId, BigDecimal availableBalance) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_accounts " +
                        "SET available_balance = :availableBalance, version = version + 1, updated_at = NOW() " +
                        "WHERE billing_account_id = :billingAccountId",
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("availableBalance", availableBalance)
        );
    }

    public long createBillingAccountLedgerEntry(long billingAccountId,
                                                int companyId,
                                                String transactionType,
                                                BigDecimal amount,
                                                String currencyCode,
                                                String direction,
                                                BigDecimal balanceBefore,
                                                BigDecimal balanceAfter,
                                                String referenceType,
                                                String referenceId,
                                                String idempotencyKey,
                                                String fundingSource,
                                                String creditReason,
                                                String approvalStatus,
                                                String description,
                                                String metadataJson) {
        String sql = "INSERT INTO public.billing_account_ledger " +
                "(billing_account_id, company_id, transaction_type, amount, currency_code, direction, balance_before, balance_after, " +
                "reference_type, reference_id, idempotency_key, funding_source, credit_reason, approval_status, description, metadata_json) " +
                "VALUES (:billingAccountId, :companyId, :transactionType, :amount, :currencyCode, :direction, :balanceBefore, :balanceAfter, " +
                ":referenceType, :referenceId, :idempotencyKey, :fundingSource, :creditReason, :approvalStatus, :description, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("companyId", companyId)
                        .addValue("transactionType", transactionType)
                        .addValue("amount", amount)
                        .addValue("currencyCode", currencyCode)
                        .addValue("direction", direction)
                        .addValue("balanceBefore", balanceBefore)
                        .addValue("balanceAfter", balanceAfter)
                        .addValue("referenceType", referenceType)
                        .addValue("referenceId", referenceId)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("fundingSource", fundingSource)
                        .addValue("creditReason", creditReason)
                        .addValue("approvalStatus", approvalStatus)
                        .addValue("description", description)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"billing_account_ledger_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public long createBillingPayment(int companyId,
                                     Long billingAccountId,
                                     String paymentSource,
                                     String providerCode,
                                     BigDecimal amount,
                                     String currencyCode,
                                     String status,
                                     String providerReference,
                                     String idempotencyKey,
                                     String metadataJson) {
        String sql = "INSERT INTO public.billing_payments " +
                "(company_id, billing_account_id, payment_source, provider_code, amount, currency_code, status, provider_reference, idempotency_key, metadata_json) " +
                "VALUES (:companyId, :billingAccountId, :paymentSource, :providerCode, :amount, :currencyCode, :status, :providerReference, :idempotencyKey, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("paymentSource", paymentSource)
                        .addValue("providerCode", providerCode)
                        .addValue("amount", amount)
                        .addValue("currencyCode", currencyCode)
                        .addValue("status", status)
                        .addValue("providerReference", providerReference)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"billing_payment_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public long createBillingPaymentAllocation(long billingPaymentId,
                                               long billingInvoiceId,
                                               BigDecimal allocatedAmount,
                                               String currencyCode) {
        String sql = "INSERT INTO public.billing_payment_allocations " +
                "(billing_payment_id, billing_invoice_id, allocated_amount, currency_code) " +
                "VALUES (:billingPaymentId, :billingInvoiceId, :allocatedAmount, :currencyCode)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingPaymentId", billingPaymentId)
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("allocatedAmount", allocatedAmount)
                        .addValue("currencyCode", currencyCode),
                keyHolder,
                new String[]{"billing_payment_allocation_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public int updateInvoicePaymentProjection(long billingInvoiceId,
                                              String status,
                                              BigDecimal paidAmount,
                                              BigDecimal dueAmount,
                                              Instant paidAt,
                                              String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_invoices " +
                        "SET status = :status, paid_amount = :paidAmount, due_amount = :dueAmount, paid_at = :paidAt, " +
                        "metadata_json = metadata_json || CAST(:metadataJson AS jsonb), version = version + 1, updated_at = NOW() " +
                        "WHERE billing_invoice_id = :billingInvoiceId",
                new MapSqlParameterSource()
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("status", status)
                        .addValue("paidAmount", paidAmount)
                        .addValue("dueAmount", dueAmount)
                        .addValue("paidAt", paidAt == null ? null : Timestamp.from(paidAt))
                        .addValue("metadataJson", metadataJson)
        );
    }

    public List<BillingPaymentAllocationReversalCandidate> findInvoicePaymentAllocationsForReversal(long billingInvoiceId) {
        String sql = "WITH original_allocations AS (" +
                " SELECT bp.billing_payment_id, bpa.billing_payment_allocation_id, bpa.billing_invoice_id, bp.billing_account_id, " +
                " bp.payment_source, bp.provider_code, bp.provider_reference, bpa.allocated_amount, bpa.currency_code, bpa.created_at " +
                " FROM public.billing_payment_allocations bpa " +
                " JOIN public.billing_payments bp ON bp.billing_payment_id = bpa.billing_payment_id " +
                " WHERE bpa.billing_invoice_id = :billingInvoiceId " +
                " AND UPPER(bp.status) = 'ALLOCATED' " +
                " AND UPPER(bp.payment_source) NOT LIKE '%REVERSAL%' " +
                " AND UPPER(bp.payment_source) NOT LIKE '%REFUND%' " +
                "), reversed_allocations AS (" +
                " SELECT (rbp.metadata_json ->> 'reversalOfAllocationId')::bigint AS original_allocation_id, " +
                " COALESCE(SUM(rbpa.allocated_amount), 0) AS reversed_amount " +
                " FROM public.billing_payments rbp " +
                " JOIN public.billing_payment_allocations rbpa ON rbpa.billing_payment_id = rbp.billing_payment_id " +
                " WHERE rbpa.billing_invoice_id = :billingInvoiceId " +
                " AND UPPER(rbp.status) = 'REVERSED' " +
                " AND jsonb_exists(rbp.metadata_json, 'reversalOfAllocationId') " +
                " GROUP BY (rbp.metadata_json ->> 'reversalOfAllocationId')::bigint" +
                ") " +
                "SELECT oa.*, GREATEST(oa.allocated_amount - COALESCE(ra.reversed_amount, 0), 0) AS reversible_amount " +
                "FROM original_allocations oa " +
                "LEFT JOIN reversed_allocations ra ON ra.original_allocation_id = oa.billing_payment_allocation_id " +
                "WHERE GREATEST(oa.allocated_amount - COALESCE(ra.reversed_amount, 0), 0) > 0 " +
                "ORDER BY oa.created_at DESC, oa.billing_payment_allocation_id DESC";
        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("billingInvoiceId", billingInvoiceId),
                PAYMENT_ALLOCATION_REVERSAL_ROW_MAPPER
        );
    }

    public int updateBillingPaymentReconciliationFields(long billingPaymentId,
                                                        BigDecimal providerGrossAmount,
                                                        BigDecimal providerFeeAmount,
                                                        BigDecimal providerNetAmount,
                                                        String settlementCurrencyCode,
                                                        String settlementDestination,
                                                        String providerSettlementReference,
                                                        String reconciliationStatus,
                                                        String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payments " +
                        "SET provider_gross_amount = :providerGrossAmount, provider_fee_amount = :providerFeeAmount, " +
                        "provider_net_amount = :providerNetAmount, settlement_currency_code = :settlementCurrencyCode, " +
                        "settlement_destination = :settlementDestination, provider_settlement_reference = :providerSettlementReference, " +
                        "reconciliation_status = :reconciliationStatus, metadata_json = metadata_json || CAST(:metadataJson AS jsonb) " +
                        "WHERE billing_payment_id = :billingPaymentId",
                new MapSqlParameterSource()
                        .addValue("billingPaymentId", billingPaymentId)
                        .addValue("providerGrossAmount", providerGrossAmount)
                        .addValue("providerFeeAmount", providerFeeAmount)
                        .addValue("providerNetAmount", providerNetAmount)
                        .addValue("settlementCurrencyCode", settlementCurrencyCode)
                        .addValue("settlementDestination", settlementDestination)
                        .addValue("providerSettlementReference", providerSettlementReference)
                        .addValue("reconciliationStatus", reconciliationStatus)
                        .addValue("metadataJson", metadataJson)
        );
    }

    public BillingBalanceSettlementSnapshot findBalanceSettlementByIdempotencyKey(int companyId,
                                                                                  long billingInvoiceId,
                                                                                  String idempotencyKey) {
        List<BillingBalanceSettlementSnapshot> items = namedParameterJdbcTemplate.query(
                "SELECT bp.billing_payment_id, bpa.billing_payment_allocation_id, bal.billing_account_ledger_id, " +
                        "bp.amount, bp.currency_code " +
                        "FROM public.billing_payments bp " +
                        "LEFT JOIN public.billing_payment_allocations bpa ON bpa.billing_payment_id = bp.billing_payment_id " +
                        "LEFT JOIN public.billing_account_ledger bal ON bal.company_id = bp.company_id " +
                        "AND bal.idempotency_key = bp.idempotency_key " +
                        "AND bal.reference_type = 'billing_invoice' " +
                        "AND bal.reference_id = :billingInvoiceReferenceId " +
                        "WHERE bp.company_id = :companyId " +
                        "AND bp.idempotency_key = :idempotencyKey " +
                        "AND bp.payment_source = 'COMPANY_BALANCE' " +
                        "AND (bpa.billing_invoice_id = :billingInvoiceId OR bpa.billing_invoice_id IS NULL) " +
                        "ORDER BY bp.billing_payment_id DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("billingInvoiceReferenceId", String.valueOf(billingInvoiceId))
                        .addValue("idempotencyKey", idempotencyKey),
                BALANCE_SETTLEMENT_SNAPSHOT_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public long createBranchSubscription(long billingAccountId,
                                         int branchId,
                                         Integer tenantId,
                                         String priceCode,
                                         String status,
                                         BigDecimal unitAmount,
                                         Date startDate,
                                         Date currentPeriodStart,
                                         Date currentPeriodEnd,
                                         String metadataJson) {
        String sql = "INSERT INTO public.branch_subscriptions " +
                "(billing_account_id, branch_id, tenant_id, price_code, status, unit_amount, start_date, current_period_start, current_period_end, metadata_json) " +
                "VALUES (:billingAccountId, :branchId, :tenantId, :priceCode, :status, :unitAmount, :startDate, :currentPeriodStart, :currentPeriodEnd, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("branchId", branchId)
                        .addValue("tenantId", tenantId)
                        .addValue("priceCode", priceCode)
                        .addValue("status", status)
                        .addValue("unitAmount", unitAmount)
                        .addValue("startDate", startDate)
                        .addValue("currentPeriodStart", currentPeriodStart)
                        .addValue("currentPeriodEnd", currentPeriodEnd)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"branch_subscription_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public boolean branchSubscriptionExistsForPeriod(int branchId, Date periodStart, Date periodEnd) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.branch_subscriptions " +
                        "WHERE branch_id = :branchId AND current_period_start = :periodStart AND current_period_end = :periodEnd",
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("periodStart", periodStart)
                        .addValue("periodEnd", periodEnd),
                Integer.class
        );
        return count != null && count > 0;
    }

    public List<BillingRenewalCandidate> findRenewalCandidates(LocalDate asOfDate, int leadDays) {
        return namedParameterJdbcTemplate.query(
                "WITH latest_paid AS (" +
                        " SELECT DISTINCT ON (bs.branch_id) " +
                        " bs.billing_account_id, bs.branch_subscription_id AS previous_branch_subscription_id, bs.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                        " bs.branch_id, b.\"branchName\" AS branch_name, ba.currency_code, bs.price_code, bs.billing_interval, bs.unit_amount, " +
                        " bs.current_period_start, bs.current_period_end " +
                        " FROM public.branch_subscriptions bs " +
                        " JOIN public.billing_accounts ba ON ba.billing_account_id = bs.billing_account_id " +
                        " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                        " JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                        " JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        " WHERE LOWER(bs.status) = 'active' AND LOWER(bi.status) = 'paid' " +
                        " ORDER BY bs.branch_id, bs.current_period_end DESC, bs.branch_subscription_id DESC" +
                        ") " +
                        "SELECT lp.* " +
                        "FROM latest_paid lp " +
                        "WHERE lp.current_period_end <= :renewalCutoff " +
                        "AND NOT EXISTS (" +
                        " SELECT 1 FROM public.branch_subscriptions future_bs " +
                        " WHERE future_bs.branch_id = lp.branch_id " +
                        " AND future_bs.current_period_start > lp.current_period_end" +
                        ")",
                new MapSqlParameterSource()
                        .addValue("renewalCutoff", Date.valueOf(asOfDate.plusDays(leadDays))),
                RENEWAL_CANDIDATE_ROW_MAPPER
        );
    }

    public int updateBranchSubscriptionStatusByExternalOrderId(String providerCode,
                                                               String externalOrderId,
                                                               String status,
                                                               String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.branch_subscriptions bs " +
                        "SET status = :status, metadata_json = CAST(:metadataJson AS jsonb), updated_at = NOW() " +
                        "FROM public.billing_invoices bi " +
                        "JOIN public.billing_payment_attempts bpa ON bpa.billing_invoice_id = bi.billing_invoice_id " +
                        "WHERE bs.branch_subscription_id = bi.source_id::bigint " +
                        "AND bi.source_type = 'branch_subscription' " +
                        "AND LOWER(bpa.provider_code) = LOWER(:providerCode) " +
                        "AND bpa.external_order_id = :externalOrderId",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("metadataJson", metadataJson)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
        );
    }

    public Long findInvoiceIdBySource(String sourceType, String sourceId) {
        List<Long> ids = namedParameterJdbcTemplate.query(
                "SELECT billing_invoice_id FROM public.billing_invoices WHERE source_type = :sourceType AND source_id = :sourceId ORDER BY billing_invoice_id ASC",
                new MapSqlParameterSource()
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId),
                (rs, rowNum) -> rs.getLong("billing_invoice_id")
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long createInvoice(long billingAccountId,
                              String invoiceNumber,
                              String status,
                              String currencyCode,
                              BigDecimal subtotalAmount,
                              BigDecimal totalAmount,
                              BigDecimal dueAmount,
                              Timestamp dueAt,
                              String sourceType,
                              String sourceId,
                              String metadataJson) {
        String sql = "INSERT INTO public.billing_invoices " +
                "(billing_account_id, invoice_number, status, currency_code, subtotal_amount, total_amount, due_amount, issued_at, due_at, source_type, source_id, metadata_json) " +
                "VALUES (:billingAccountId, :invoiceNumber, :status, :currencyCode, :subtotalAmount, :totalAmount, :dueAmount, NOW(), :dueAt, :sourceType, :sourceId, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("invoiceNumber", invoiceNumber)
                        .addValue("status", status)
                        .addValue("currencyCode", currencyCode)
                        .addValue("subtotalAmount", subtotalAmount)
                        .addValue("totalAmount", totalAmount)
                        .addValue("dueAmount", dueAmount)
                        .addValue("dueAt", dueAt)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"billing_invoice_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public boolean invoiceLineExists(long invoiceId, Long branchSubscriptionId) {
        Integer count = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.billing_invoice_lines WHERE billing_invoice_id = :invoiceId AND " +
                        "((:branchSubscriptionId IS NULL AND branch_subscription_id IS NULL) OR branch_subscription_id = :branchSubscriptionId)",
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("branchSubscriptionId", branchSubscriptionId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public void createInvoiceLine(long invoiceId,
                                  Long branchSubscriptionId,
                                  String lineDescription,
                                  int quantity,
                                  BigDecimal unitAmount,
                                  BigDecimal lineTotal,
                                  String metadataJson) {
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.billing_invoice_lines " +
                        "(billing_invoice_id, branch_subscription_id, line_description, quantity, unit_amount, line_total, metadata_json) " +
                        "VALUES (:invoiceId, :branchSubscriptionId, :lineDescription, :quantity, :unitAmount, :lineTotal, CAST(:metadataJson AS jsonb))",
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("branchSubscriptionId", branchSubscriptionId)
                        .addValue("lineDescription", lineDescription)
                        .addValue("quantity", quantity)
                        .addValue("unitAmount", unitAmount)
                        .addValue("lineTotal", lineTotal)
                        .addValue("metadataJson", metadataJson)
        );
    }

    public Long findPaymentAttemptId(String providerCode, String externalOrderId) {
        List<Long> ids = namedParameterJdbcTemplate.query(
                "SELECT billing_payment_attempt_id FROM public.billing_payment_attempts " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) AND external_order_id = :externalOrderId " +
                        "ORDER BY billing_payment_attempt_id ASC",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId),
                (rs, rowNum) -> rs.getLong("billing_payment_attempt_id")
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public BillingPaymentAttemptValidationContext findPaymentAttemptValidationContext(String providerCode, String externalOrderId) {
        return findPaymentAttemptValidationContext(providerCode, externalOrderId, false);
    }

    public BillingPaymentAttemptValidationContext lockPaymentAttemptValidationContext(String providerCode, String externalOrderId) {
        return findPaymentAttemptValidationContext(providerCode, externalOrderId, true);
    }

    private BillingPaymentAttemptValidationContext findPaymentAttemptValidationContext(String providerCode,
                                                                                      String externalOrderId,
                                                                                      boolean lockRow) {
        String sql = "SELECT billing_payment_attempt_id, billing_invoice_id, company_id, branch_id, requested_amount, currency_code, status, external_payment_reference " +
                "FROM public.billing_payment_attempts " +
                "WHERE LOWER(provider_code) = LOWER(:providerCode) AND external_order_id = :externalOrderId " +
                "ORDER BY billing_payment_attempt_id DESC";
        if (lockRow) {
            sql = sql + " FOR UPDATE";
        }
        List<BillingPaymentAttemptValidationContext> items = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId),
                (rs, rowNum) -> new BillingPaymentAttemptValidationContext(
                        rs.getLong("billing_payment_attempt_id"),
                        rs.getLong("billing_invoice_id"),
                        rs.getObject("company_id", Integer.class),
                        rs.getObject("branch_id", Integer.class),
                        rs.getBigDecimal("requested_amount"),
                        rs.getString("currency_code"),
                        rs.getString("status"),
                        rs.getString("external_payment_reference")
                )
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public long createPaymentAttempt(long invoiceId,
                                     String providerCode,
                                     String externalOrderId,
                                     String status,
                                     BigDecimal requestedAmount,
                                     String currencyCode,
                                     String requestPayloadJson,
                                     String providerResponseJson) {
        String sql = "INSERT INTO public.billing_payment_attempts " +
                "(billing_invoice_id, provider_code, external_order_id, status, requested_amount, currency_code, request_payload_json, provider_response_json) " +
                "VALUES (:invoiceId, :providerCode, :externalOrderId, :status, :requestedAmount, :currencyCode, CAST(:requestPayloadJson AS jsonb), CAST(:providerResponseJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
                        .addValue("status", status)
                        .addValue("requestedAmount", requestedAmount)
                        .addValue("currencyCode", currencyCode)
                        .addValue("requestPayloadJson", requestPayloadJson)
                        .addValue("providerResponseJson", providerResponseJson),
                keyHolder,
                new String[]{"billing_payment_attempt_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public int supersedeActivePaymentAttempts(long invoiceId, String providerCode) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payment_attempts " +
                        "SET status = 'SUPERSEDED', terminal_at = COALESCE(terminal_at, NOW()) " +
                        "WHERE billing_invoice_id = :invoiceId " +
                        "AND LOWER(provider_code) = LOWER(:providerCode) " +
                        "AND UPPER(status) IN ('CREATED', 'CHECKOUT_PENDING', 'CHECKOUT_REQUESTED', 'PENDING_PROVIDER')",
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("providerCode", providerCode)
        );
    }

    public BillingPaymentAttemptSnapshot findPaymentAttemptByCompanyIdempotency(int companyId, String idempotencyKey) {
        List<BillingPaymentAttemptSnapshot> items = namedParameterJdbcTemplate.query(
                "SELECT billing_payment_attempt_id, provider_code, external_order_id, status, requested_amount, currency_code, " +
                        "provider_response_json ->> 'checkoutUrl' AS checkout_url " +
                        "FROM public.billing_payment_attempts " +
                        "WHERE company_id = :companyId " +
                        "AND idempotency_key = :idempotencyKey " +
                        "ORDER BY billing_payment_attempt_id DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("idempotencyKey", idempotencyKey),
                PAYMENT_ATTEMPT_SNAPSHOT_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public BillingPaymentAttemptSnapshot findLatestPaymentAttemptByInvoiceId(long invoiceId) {
        List<BillingPaymentAttemptSnapshot> items = namedParameterJdbcTemplate.query(
                "SELECT billing_payment_attempt_id, provider_code, external_order_id, status, requested_amount, currency_code, " +
                        "provider_response_json ->> 'checkoutUrl' AS checkout_url " +
                        "FROM public.billing_payment_attempts " +
                        "WHERE billing_invoice_id = :invoiceId " +
                        "ORDER BY billing_payment_attempt_id DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId),
                PAYMENT_ATTEMPT_SNAPSHOT_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public long createPaymentAttempt(long invoiceId,
                                     Integer companyId,
                                     Integer branchId,
                                     String idempotencyKey,
                                     String providerCode,
                                     String externalOrderId,
                                     String status,
                                     BigDecimal requestedAmount,
                                     String currencyCode,
                                     String checkoutReference,
                                     String requestPayloadJson,
                                     String providerResponseJson,
                                     String metadataJson) {
        String sql = "INSERT INTO public.billing_payment_attempts " +
                "(billing_invoice_id, company_id, branch_id, idempotency_key, provider_code, external_order_id, status, requested_amount, currency_code, " +
                "checkout_reference, checkout_requested_at, request_payload_json, provider_response_json, metadata_json) " +
                "VALUES (:invoiceId, :companyId, :branchId, :idempotencyKey, :providerCode, :externalOrderId, :status, :requestedAmount, :currencyCode, " +
                ":checkoutReference, NOW(), CAST(:requestPayloadJson AS jsonb), CAST(:providerResponseJson AS jsonb), CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
                        .addValue("status", status)
                        .addValue("requestedAmount", requestedAmount)
                        .addValue("currencyCode", currencyCode)
                        .addValue("checkoutReference", checkoutReference)
                        .addValue("requestPayloadJson", requestPayloadJson)
                        .addValue("providerResponseJson", providerResponseJson)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"billing_payment_attempt_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public long createProviderCheckoutOutbox(long billingPaymentAttemptId,
                                             String providerCode,
                                             String operationType,
                                             String idempotencyKey,
                                             String requestPayloadJson) {
        String sql = "INSERT INTO public.billing_provider_checkout_outbox " +
                "(billing_payment_attempt_id, provider_code, operation_type, idempotency_key, request_payload_json) " +
                "VALUES (:billingPaymentAttemptId, :providerCode, :operationType, :idempotencyKey, CAST(:requestPayloadJson AS jsonb)) " +
                "ON CONFLICT (provider_code, idempotency_key) DO UPDATE SET " +
                "request_payload_json = EXCLUDED.request_payload_json, " +
                "status = CASE " +
                "    WHEN public.billing_provider_checkout_outbox.status = 'SUCCEEDED' THEN public.billing_provider_checkout_outbox.status " +
                "    ELSE 'PENDING' " +
                "END, " +
                "last_error = NULL, next_attempt_at = NOW() " +
                "RETURNING checkout_outbox_id";
        Long checkoutOutboxId = namedParameterJdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingPaymentAttemptId", billingPaymentAttemptId)
                        .addValue("providerCode", providerCode)
                        .addValue("operationType", operationType)
                        .addValue("idempotencyKey", idempotencyKey)
                        .addValue("requestPayloadJson", requestPayloadJson),
                Long.class
        );
        return checkoutOutboxId == null ? 0L : checkoutOutboxId;
    }

    public List<BillingProviderCheckoutOutboxItem> claimDueProviderCheckoutOutboxItems(int limit) {
        String sql = "WITH candidates AS ( " +
                "    SELECT checkout_outbox_id " +
                "    FROM public.billing_provider_checkout_outbox " +
                "    WHERE status IN ('PENDING', 'FAILED_RETRYABLE') " +
                "      AND next_attempt_at <= NOW() " +
                "    ORDER BY next_attempt_at ASC, checkout_outbox_id ASC " +
                "    LIMIT :limit " +
                "    FOR UPDATE SKIP LOCKED " +
                "), updated AS ( " +
                "    UPDATE public.billing_provider_checkout_outbox outbox " +
                "    SET status = 'PROCESSING', attempt_count = outbox.attempt_count + 1, updated_at = NOW() " +
                "    FROM candidates " +
                "    WHERE outbox.checkout_outbox_id = candidates.checkout_outbox_id " +
                "    RETURNING outbox.checkout_outbox_id, outbox.billing_payment_attempt_id, outbox.provider_code, " +
                "        outbox.operation_type, outbox.idempotency_key, outbox.request_payload_json::text AS request_payload_json, " +
                "        outbox.attempt_count " +
                ") " +
                "SELECT updated.checkout_outbox_id, updated.billing_payment_attempt_id, bpa.billing_invoice_id, " +
                "       COALESCE(bpa.company_id, 0) AS company_id, bpa.branch_id, updated.provider_code, updated.operation_type, " +
                "       updated.idempotency_key, bpa.requested_amount, bpa.currency_code, bpa.checkout_reference, " +
                "       updated.request_payload_json, updated.attempt_count " +
                "FROM updated " +
                "JOIN public.billing_payment_attempts bpa ON bpa.billing_payment_attempt_id = updated.billing_payment_attempt_id";
        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("limit", limit),
                PROVIDER_CHECKOUT_OUTBOX_ROW_MAPPER
        );
    }

    public int markProviderCheckoutOutboxSucceeded(long checkoutOutboxId, String responsePayloadJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_provider_checkout_outbox " +
                        "SET status = 'SUCCEEDED', response_payload_json = CAST(:responsePayloadJson AS jsonb), " +
                        "last_error = NULL, updated_at = NOW() " +
                        "WHERE checkout_outbox_id = :checkoutOutboxId",
                new MapSqlParameterSource()
                        .addValue("checkoutOutboxId", checkoutOutboxId)
                        .addValue("responsePayloadJson", responsePayloadJson)
        );
    }

    public int markProviderCheckoutOutboxRetryable(long checkoutOutboxId,
                                                   String errorMessage,
                                                   int delaySeconds) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_provider_checkout_outbox " +
                        "SET status = 'FAILED_RETRYABLE', last_error = :errorMessage, " +
                        "next_attempt_at = NOW() + (:delaySeconds * INTERVAL '1 second'), updated_at = NOW() " +
                        "WHERE checkout_outbox_id = :checkoutOutboxId",
                new MapSqlParameterSource()
                        .addValue("checkoutOutboxId", checkoutOutboxId)
                        .addValue("errorMessage", errorMessage)
                        .addValue("delaySeconds", delaySeconds)
        );
    }

    public int markProviderCheckoutOutboxFinal(long checkoutOutboxId,
                                               String status,
                                               String responsePayloadJson,
                                               String errorMessage) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_provider_checkout_outbox " +
                        "SET status = :status, response_payload_json = CAST(:responsePayloadJson AS jsonb), " +
                        "last_error = :errorMessage, updated_at = NOW() " +
                        "WHERE checkout_outbox_id = :checkoutOutboxId",
                new MapSqlParameterSource()
                        .addValue("checkoutOutboxId", checkoutOutboxId)
                        .addValue("status", status)
                        .addValue("responsePayloadJson", responsePayloadJson)
                        .addValue("errorMessage", errorMessage)
        );
    }

    public int updatePaymentAttemptCheckoutRequestedById(long billingPaymentAttemptId,
                                                         String externalOrderId,
                                                         String checkoutReference,
                                                         String providerResponseJson,
                                                         String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payment_attempts " +
                        "SET external_order_id = :externalOrderId, checkout_reference = COALESCE(:checkoutReference, checkout_reference), " +
                        "status = 'CHECKOUT_REQUESTED', checkout_requested_at = NOW(), " +
                        "provider_response_json = CAST(:providerResponseJson AS jsonb), " +
                        "metadata_json = metadata_json || CAST(:metadataJson AS jsonb) " +
                        "WHERE billing_payment_attempt_id = :billingPaymentAttemptId " +
                        "AND UPPER(status) IN ('CREATED', 'CHECKOUT_PENDING', 'PENDING_PROVIDER')",
                new MapSqlParameterSource()
                        .addValue("billingPaymentAttemptId", billingPaymentAttemptId)
                        .addValue("externalOrderId", externalOrderId)
                        .addValue("checkoutReference", checkoutReference)
                        .addValue("providerResponseJson", providerResponseJson)
                        .addValue("metadataJson", metadataJson)
        );
    }

    public int updatePaymentAttemptPendingProviderById(long billingPaymentAttemptId,
                                                       String externalOrderId,
                                                       String providerResponseJson,
                                                       String failureCode,
                                                       String failureMessage) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payment_attempts " +
                        "SET external_order_id = COALESCE(:externalOrderId, external_order_id), status = 'PENDING_PROVIDER', " +
                        "provider_response_json = CAST(:providerResponseJson AS jsonb), " +
                        "failure_code = :failureCode, failure_message = :failureMessage " +
                        "WHERE billing_payment_attempt_id = :billingPaymentAttemptId " +
                        "AND UPPER(status) IN ('CREATED', 'CHECKOUT_PENDING', 'PENDING_PROVIDER')",
                new MapSqlParameterSource()
                        .addValue("billingPaymentAttemptId", billingPaymentAttemptId)
                        .addValue("externalOrderId", externalOrderId)
                        .addValue("providerResponseJson", providerResponseJson)
                        .addValue("failureCode", failureCode)
                        .addValue("failureMessage", failureMessage)
        );
    }

    public BillingInvoiceMutationContext findInvoiceMutationContext(long billingInvoiceId) {
        List<BillingInvoiceMutationContext> items = namedParameterJdbcTemplate.query(
                "SELECT bi.billing_invoice_id, bi.billing_account_id, bs.branch_subscription_id, ba.tenant_id, ba.company_id, bs.branch_id, " +
                        "bi.status, bi.total_amount, bi.paid_amount, bi.due_amount, bi.currency_code " +
                        "FROM public.billing_invoices bi " +
                        "JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                        "LEFT JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        "WHERE bi.billing_invoice_id = :billingInvoiceId",
                new MapSqlParameterSource().addValue("billingInvoiceId", billingInvoiceId),
                (rs, rowNum) -> new BillingInvoiceMutationContext(
                        rs.getLong("billing_invoice_id"),
                        rs.getLong("billing_account_id"),
                        (Long) rs.getObject("branch_subscription_id"),
                        rs.getInt("tenant_id"),
                        rs.getInt("company_id"),
                        (Integer) rs.getObject("branch_id"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("due_amount"),
                        rs.getString("currency_code")
                )
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public long createCompletedPaymentAttempt(long invoiceId,
                                              String providerCode,
                                              String externalOrderId,
                                              String externalPaymentReference,
                                              String status,
                                              BigDecimal requestedAmount,
                                              String currencyCode,
                                              String requestPayloadJson,
                                              String providerResponseJson) {
        String sql = "INSERT INTO public.billing_payment_attempts " +
                "(billing_invoice_id, provider_code, external_order_id, external_payment_reference, status, requested_amount, currency_code, request_payload_json, provider_response_json, completed_at) " +
                "VALUES (:invoiceId, :providerCode, :externalOrderId, :externalPaymentReference, :status, :requestedAmount, :currencyCode, CAST(:requestPayloadJson AS jsonb), CAST(:providerResponseJson AS jsonb), NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("invoiceId", invoiceId)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
                        .addValue("externalPaymentReference", externalPaymentReference)
                        .addValue("status", status)
                        .addValue("requestedAmount", requestedAmount)
                        .addValue("currencyCode", currencyCode)
                        .addValue("requestPayloadJson", requestPayloadJson)
                        .addValue("providerResponseJson", providerResponseJson),
                keyHolder,
                new String[]{"billing_payment_attempt_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public int updateInvoiceManualState(long billingInvoiceId,
                                        String status,
                                        BigDecimal paidAmount,
                                        BigDecimal dueAmount,
                                        Instant paidAt,
                                        String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_invoices " +
                        "SET status = :status, paid_amount = :paidAmount, due_amount = :dueAmount, paid_at = :paidAt, " +
                        "metadata_json = metadata_json || CAST(:metadataJson AS jsonb), updated_at = NOW() " +
                        "WHERE billing_invoice_id = :billingInvoiceId",
                new MapSqlParameterSource()
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("status", status)
                        .addValue("paidAmount", paidAmount)
                        .addValue("dueAmount", dueAmount)
                        .addValue("paidAt", paidAt == null ? null : Timestamp.from(paidAt))
                        .addValue("metadataJson", metadataJson)
        );
    }

    public int updateBranchSubscriptionStatusById(long branchSubscriptionId,
                                                  String status,
                                                  String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.branch_subscriptions " +
                        "SET status = :status, metadata_json = metadata_json || CAST(:metadataJson AS jsonb), updated_at = NOW() " +
                        "WHERE branch_subscription_id = :branchSubscriptionId",
                new MapSqlParameterSource()
                        .addValue("branchSubscriptionId", branchSubscriptionId)
                        .addValue("status", status)
                        .addValue("metadataJson", metadataJson)
        );
    }

    public int updatePaymentAttemptCheckoutRequest(String providerCode,
                                                   String externalOrderId,
                                                   String status,
                                                   String providerResponseJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payment_attempts " +
                        "SET status = :status, provider_response_json = CAST(:providerResponseJson AS jsonb) " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) " +
                        "AND external_order_id = :externalOrderId " +
                        "AND UPPER(status) NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'SUPERSEDED')",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("providerResponseJson", providerResponseJson)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
        );
    }

    public int completePaymentAttempt(String providerCode,
                                      String externalOrderId,
                                      String status,
                                      String providerResponseJson,
                                      String externalPaymentReference,
                                      String failureCode,
                                      String failureMessage) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payment_attempts " +
                        "SET status = :status, provider_response_json = CAST(:providerResponseJson AS jsonb), " +
                        "external_payment_reference = :externalPaymentReference, failure_code = :failureCode, failure_message = :failureMessage, completed_at = NOW() " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) " +
                        "AND external_order_id = :externalOrderId " +
                        "AND UPPER(status) NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'SUPERSEDED')",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("providerResponseJson", providerResponseJson)
                        .addValue("externalPaymentReference", externalPaymentReference)
                        .addValue("failureCode", failureCode)
                        .addValue("failureMessage", failureMessage)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
        );
    }

    public int updateInvoiceStatusByExternalOrderId(String providerCode,
                                                    String externalOrderId,
                                                    String status,
                                                    BigDecimal dueAmount,
                                                    Instant paidAt,
                                                    String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_invoices bi " +
                        "SET status = :status, due_amount = :dueAmount, paid_at = :paidAt, metadata_json = CAST(:metadataJson AS jsonb), updated_at = NOW() " +
                        "FROM public.billing_payment_attempts bpa " +
                        "WHERE bpa.billing_invoice_id = bi.billing_invoice_id " +
                        "AND LOWER(bpa.provider_code) = LOWER(:providerCode) " +
                        "AND bpa.external_order_id = :externalOrderId",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("dueAmount", dueAmount)
                        .addValue("paidAt", paidAt == null ? null : Timestamp.from(paidAt))
                        .addValue("metadataJson", metadataJson)
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId)
        );
    }

    public int updateInvoiceStatusBySource(String sourceType,
                                           String sourceId,
                                           String status,
                                           BigDecimal dueAmount,
                                           Instant paidAt,
                                           String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_invoices " +
                        "SET status = :status, due_amount = :dueAmount, paid_at = :paidAt, metadata_json = CAST(:metadataJson AS jsonb), updated_at = NOW() " +
                        "WHERE source_type = :sourceType AND source_id = :sourceId",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("dueAmount", dueAmount)
                        .addValue("paidAt", paidAt == null ? null : Timestamp.from(paidAt))
                        .addValue("metadataJson", metadataJson)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId)
        );
    }

    public void upsertProviderEvent(String providerCode,
                                    String providerEventId,
                                    String eventType,
                                    String externalReference,
                                    String payloadJson,
                                    String processingStatus,
                                    String errorMessage) {
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.billing_provider_events " +
                        "(provider_code, provider_event_id, event_type, external_reference, payload_json, processing_status, processed_at, error_message) " +
                        "VALUES (:providerCode, :providerEventId, :eventType, :externalReference, CAST(:payloadJson AS jsonb), :processingStatus, NOW(), :errorMessage) " +
                        "ON CONFLICT (provider_code, provider_event_id) DO UPDATE SET " +
                        "event_type = EXCLUDED.event_type, external_reference = EXCLUDED.external_reference, payload_json = EXCLUDED.payload_json, " +
                        "processing_status = EXCLUDED.processing_status, processed_at = EXCLUDED.processed_at, error_message = EXCLUDED.error_message",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("providerEventId", providerEventId)
                        .addValue("eventType", eventType)
                        .addValue("externalReference", externalReference)
                        .addValue("payloadJson", payloadJson)
                        .addValue("processingStatus", processingStatus)
                        .addValue("errorMessage", errorMessage)
        );
    }

    public boolean reserveProviderEventProcessing(String providerCode,
                                                  String providerEventId,
                                                  String eventType,
                                                  String externalReference,
                                                  String payloadJson) {
        int rows = namedParameterJdbcTemplate.update(
                "INSERT INTO public.billing_provider_events " +
                        "(provider_code, provider_event_id, event_type, external_reference, payload_json, processing_status, received_at, locked_at) " +
                        "VALUES (:providerCode, :providerEventId, :eventType, :externalReference, CAST(:payloadJson AS jsonb), 'processing', NOW(), NOW()) " +
                        "ON CONFLICT (provider_code, provider_event_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("providerEventId", providerEventId)
                        .addValue("eventType", eventType)
                        .addValue("externalReference", externalReference)
                        .addValue("payloadJson", payloadJson)
        );
        return rows > 0;
    }

    public int markProviderEventStatus(String providerCode,
                                       String providerEventId,
                                       String processingStatus,
                                       Long attemptId,
                                       Long billingInvoiceId,
                                       Integer companyId,
                                       String errorMessage) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_provider_events " +
                        "SET processing_status = :processingStatus, processed_at = NOW(), locked_at = NULL, " +
                        "attempt_id = :attemptId, billing_invoice_id = :billingInvoiceId, company_id = :companyId, " +
                        "error_message = :errorMessage " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) AND provider_event_id = :providerEventId",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("providerEventId", providerEventId)
                        .addValue("processingStatus", processingStatus)
                        .addValue("attemptId", attemptId)
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("companyId", companyId)
                        .addValue("errorMessage", errorMessage)
        );
    }

    public String findProviderEventStatus(String providerCode, String providerEventId) {
        List<String> items = namedParameterJdbcTemplate.query(
                "SELECT processing_status FROM public.billing_provider_events " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) AND provider_event_id = :providerEventId",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("providerEventId", providerEventId),
                (rs, rowNum) -> rs.getString("processing_status")
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public void createEntitlementEvent(int branchId,
                                       Long branchSubscriptionId,
                                       Long billingInvoiceId,
                                       String eventType,
                                       String fromState,
                                       String toState,
                                       String reasonCode,
                                       String metadataJson) {
        namedParameterJdbcTemplate.update(
                "INSERT INTO public.billing_entitlement_events " +
                        "(branch_id, branch_subscription_id, billing_invoice_id, event_type, from_state, to_state, reason_code, metadata_json) " +
                        "VALUES (:branchId, :branchSubscriptionId, :billingInvoiceId, :eventType, :fromState, :toState, :reasonCode, CAST(:metadataJson AS jsonb))",
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("branchSubscriptionId", branchSubscriptionId)
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("eventType", eventType)
                        .addValue("fromState", fromState)
                        .addValue("toState", toState)
                        .addValue("reasonCode", reasonCode)
                        .addValue("metadataJson", metadataJson)
        );
    }

    public List<BillingOverdueInvoiceCandidate> findOverdueInvoices(LocalDate asOfDate,
                                                                    int graceDays,
                                                                    int maxAttempts) {
        return namedParameterJdbcTemplate.query(
                "WITH latest_attempts AS (" +
                        " SELECT DISTINCT ON (bpa.billing_invoice_id) " +
                        " bpa.billing_invoice_id, bpa.provider_code " +
                        " FROM public.billing_payment_attempts bpa " +
                        " ORDER BY bpa.billing_invoice_id, bpa.billing_payment_attempt_id DESC" +
                        "), dunning_counts AS (" +
                        " SELECT billing_invoice_id, COUNT(*) AS existing_dunning_attempts " +
                        " FROM public.billing_dunning_runs GROUP BY billing_invoice_id" +
                        ") " +
                        "SELECT bi.billing_invoice_id, bi.billing_account_id, bs.branch_subscription_id, ba.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                        " bs.branch_id, b.\"branchName\" AS branch_name, COALESCE(la.provider_code, '') AS provider_code, bi.currency_code, bi.total_amount, bi.due_amount, bi.due_at, " +
                        " COALESCE(dc.existing_dunning_attempts, 0) AS existing_dunning_attempts " +
                        "FROM public.billing_invoices bi " +
                        "JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                        "JOIN public.\"Company\" c ON c.id = ba.company_id " +
                        "JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        "JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                        "LEFT JOIN latest_attempts la ON la.billing_invoice_id = bi.billing_invoice_id " +
                        "LEFT JOIN dunning_counts dc ON dc.billing_invoice_id = bi.billing_invoice_id " +
                        "WHERE LOWER(bi.status) = 'open' " +
                        "AND bi.due_at IS NOT NULL " +
                        "AND bi.due_at < :overdueThreshold " +
                        "AND COALESCE(dc.existing_dunning_attempts, 0) < :maxAttempts " +
                        "ORDER BY bi.due_at ASC, bi.billing_invoice_id ASC",
                new MapSqlParameterSource()
                        .addValue("overdueThreshold", Timestamp.valueOf(asOfDate.atStartOfDay().minusDays(graceDays)))
                        .addValue("maxAttempts", maxAttempts),
                OVERDUE_INVOICE_ROW_MAPPER
        );
    }

    public long createDunningRun(long billingAccountId,
                                 long billingInvoiceId,
                                 String status,
                                 int attemptNumber,
                                 Timestamp scheduledAt,
                                 String metadataJson) {
        String sql = "INSERT INTO public.billing_dunning_runs " +
                "(billing_account_id, billing_invoice_id, status, attempt_number, scheduled_at, metadata_json) " +
                "VALUES (:billingAccountId, :billingInvoiceId, :status, :attemptNumber, :scheduledAt, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("billingInvoiceId", billingInvoiceId)
                        .addValue("status", status)
                        .addValue("attemptNumber", attemptNumber)
                        .addValue("scheduledAt", scheduledAt)
                        .addValue("metadataJson", metadataJson),
                keyHolder,
                new String[]{"billing_dunning_run_id"}
        );
        return keyHolder.getKey().longValue();
    }

    public int completeDunningRun(long billingDunningRunId,
                                  String status,
                                  String resultSummary) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_dunning_runs " +
                        "SET status = :status, executed_at = NOW(), result_summary = :resultSummary " +
                        "WHERE billing_dunning_run_id = :billingDunningRunId",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("resultSummary", resultSummary)
                        .addValue("billingDunningRunId", billingDunningRunId)
        );
    }

    public Integer findBranchIdByExternalOrderId(String providerCode, String externalOrderId) {
        List<Integer> ids = namedParameterJdbcTemplate.query(
                "SELECT bs.branch_id " +
                        "FROM public.branch_subscriptions bs " +
                        "JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        "JOIN public.billing_payment_attempts bpa ON bpa.billing_invoice_id = bi.billing_invoice_id " +
                        "WHERE LOWER(bpa.provider_code) = LOWER(:providerCode) AND bpa.external_order_id = :externalOrderId " +
                        "ORDER BY bs.branch_subscription_id ASC",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId),
                (rs, rowNum) -> rs.getInt("branch_id")
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public BillingInvoiceRetryCandidate findInvoiceRetryCandidate(long billingInvoiceId) {
        List<BillingInvoiceRetryCandidate> items = namedParameterJdbcTemplate.query(
                "WITH latest_attempts AS (" +
                        " SELECT DISTINCT ON (bpa.billing_invoice_id) bpa.billing_invoice_id, bpa.provider_code " +
                        " FROM public.billing_payment_attempts bpa " +
                        " ORDER BY bpa.billing_invoice_id, bpa.billing_payment_attempt_id DESC" +
                        "), attempt_summary AS (" +
                        " SELECT bpa.billing_invoice_id, COUNT(*) AS attempt_count, " +
                        " MAX(COALESCE(bpa.completed_at, bpa.attempted_at)) AS latest_attempt_at " +
                        " FROM public.billing_payment_attempts bpa " +
                        " GROUP BY bpa.billing_invoice_id" +
                        ") " +
                        "SELECT bi.billing_invoice_id, bs.branch_subscription_id, bi.billing_account_id, ba.tenant_id, ba.company_id, bs.branch_id, " +
                        " COALESCE(la.provider_code, '') AS provider_code, bi.currency_code, bi.due_amount, bi.status AS invoice_status, " +
                        " COALESCE(asu.attempt_count, 0) AS attempt_count, asu.latest_attempt_at " +
                        "FROM public.billing_invoices bi " +
                        "JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                        "JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        "LEFT JOIN latest_attempts la ON la.billing_invoice_id = bi.billing_invoice_id " +
                        "LEFT JOIN attempt_summary asu ON asu.billing_invoice_id = bi.billing_invoice_id " +
                        "WHERE bi.billing_invoice_id = :billingInvoiceId",
                new MapSqlParameterSource().addValue("billingInvoiceId", billingInvoiceId),
                INVOICE_RETRY_CANDIDATE_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public BranchBillingCheckoutCandidate findBranchCheckoutCandidate(int branchId) {
        List<BranchBillingCheckoutCandidate> items = namedParameterJdbcTemplate.query(
                "WITH latest_attempt AS (" +
                        " SELECT DISTINCT ON (bpa.billing_invoice_id) " +
                        " bpa.billing_invoice_id, bpa.status AS latest_attempt_status, bpa.external_order_id AS latest_external_order_id " +
                        " FROM public.billing_payment_attempts bpa " +
                        " ORDER BY bpa.billing_invoice_id, bpa.billing_payment_attempt_id DESC" +
                        ") " +
                        "SELECT bi.billing_invoice_id, bs.branch_subscription_id, bi.billing_account_id, ba.tenant_id, ba.company_id, bs.branch_id, " +
                        " bi.currency_code, bi.due_amount, bi.status AS invoice_status, la.latest_attempt_status, la.latest_external_order_id " +
                        "FROM public.branch_subscriptions bs " +
                        "JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        "JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                        "LEFT JOIN latest_attempt la ON la.billing_invoice_id = bi.billing_invoice_id " +
                        "WHERE bs.branch_id = :branchId " +
                        "AND COALESCE(bi.due_amount, 0) > 0 " +
                        "ORDER BY bs.branch_subscription_id DESC " +
                        "LIMIT 1",
                new MapSqlParameterSource().addValue("branchId", branchId),
                BRANCH_CHECKOUT_CANDIDATE_ROW_MAPPER
        );
        return items.isEmpty() ? null : items.get(0);
    }
}
