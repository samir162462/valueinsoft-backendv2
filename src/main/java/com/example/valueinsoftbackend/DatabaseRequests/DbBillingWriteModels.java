package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Billing.BillingOverdueInvoiceCandidate;
import com.example.valueinsoftbackend.Model.Billing.BillingPaymentAttemptValidationContext;
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

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbBillingWriteModels(JdbcTemplate jdbcTemplate,
                                NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public Long findBillingAccountIdByCompanyId(int companyId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT billing_account_id FROM public.billing_accounts WHERE company_id = ?",
                (rs, rowNum) -> rs.getLong("billing_account_id"),
                companyId
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

    public Long findBranchSubscriptionIdByLegacySubscriptionId(int legacySubscriptionId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT branch_subscription_id FROM public.branch_subscriptions WHERE legacy_subscription_id = ? ORDER BY branch_subscription_id ASC",
                (rs, rowNum) -> rs.getLong("branch_subscription_id"),
                legacySubscriptionId
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long createBranchSubscription(long billingAccountId,
                                         int branchId,
                                         Integer tenantId,
                                         int legacySubscriptionId,
                                         String priceCode,
                                         String status,
                                         BigDecimal unitAmount,
                                         Date startDate,
                                         Date currentPeriodStart,
                                         Date currentPeriodEnd,
                                         String metadataJson) {
        String sql = "INSERT INTO public.branch_subscriptions " +
                "(billing_account_id, branch_id, tenant_id, legacy_subscription_id, price_code, status, unit_amount, start_date, current_period_start, current_period_end, metadata_json) " +
                "VALUES (:billingAccountId, :branchId, :tenantId, :legacySubscriptionId, :priceCode, :status, :unitAmount, :startDate, :currentPeriodStart, :currentPeriodEnd, CAST(:metadataJson AS jsonb))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("branchId", branchId)
                        .addValue("tenantId", tenantId)
                        .addValue("legacySubscriptionId", legacySubscriptionId)
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

    public int updateBranchSubscriptionStatusByLegacySubscriptionId(int legacySubscriptionId,
                                                                    String status,
                                                                    String metadataJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.branch_subscriptions " +
                        "SET status = :status, metadata_json = CAST(:metadataJson AS jsonb), updated_at = NOW() " +
                        "WHERE legacy_subscription_id = :legacySubscriptionId",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("metadataJson", metadataJson)
                        .addValue("legacySubscriptionId", legacySubscriptionId)
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
        List<BillingPaymentAttemptValidationContext> items = namedParameterJdbcTemplate.query(
                "SELECT billing_payment_attempt_id, billing_invoice_id, requested_amount, currency_code, status, external_payment_reference " +
                        "FROM public.billing_payment_attempts " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) AND external_order_id = :externalOrderId " +
                        "ORDER BY billing_payment_attempt_id DESC",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId),
                (rs, rowNum) -> new BillingPaymentAttemptValidationContext(
                        rs.getLong("billing_payment_attempt_id"),
                        rs.getLong("billing_invoice_id"),
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

    public int updatePaymentAttemptCheckoutRequest(String providerCode,
                                                   String externalOrderId,
                                                   String status,
                                                   String providerResponseJson) {
        return namedParameterJdbcTemplate.update(
                "UPDATE public.billing_payment_attempts " +
                        "SET status = :status, provider_response_json = CAST(:providerResponseJson AS jsonb) " +
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) AND external_order_id = :externalOrderId",
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
                        "WHERE LOWER(provider_code) = LOWER(:providerCode) AND external_order_id = :externalOrderId",
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
