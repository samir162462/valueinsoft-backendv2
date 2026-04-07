package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingInvoiceItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingDunningRunItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingDunningRunsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingEntitlementItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingEntitlementsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingHealthSnapshotResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingInvoicesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingPackageSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingPaymentAttemptItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingPaymentAttemptsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingReconciliationItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingReconciliationPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingRenewalBacklogItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingRenewalBacklogPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSummaryResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

@Repository
public class DbBillingAdminReadModels {

    private static final RowMapper<PlatformBillingInvoiceItem> INVOICE_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingInvoiceItem(
                    rs.getLong("billing_invoice_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    (Integer) rs.getObject("branch_id"),
                    rs.getString("branch_name"),
                    (Long) rs.getObject("branch_subscription_id"),
                    rs.getString("invoice_number"),
                    rs.getString("status"),
                    getBigDecimal(rs.getObject("total_amount")),
                    getBigDecimal(rs.getObject("due_amount")),
                    rs.getString("currency_code"),
                    rs.getString("source_type"),
                    rs.getString("source_id"),
                    rs.getTimestamp("issued_at"),
                    rs.getTimestamp("due_at"),
                    rs.getTimestamp("paid_at")
            );

    private static final RowMapper<PlatformBillingPaymentAttemptItem> PAYMENT_ATTEMPT_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingPaymentAttemptItem(
                    rs.getLong("billing_payment_attempt_id"),
                    rs.getLong("billing_invoice_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    (Integer) rs.getObject("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("provider_code"),
                    rs.getString("external_order_id"),
                    rs.getString("external_payment_reference"),
                    rs.getString("status"),
                    getBigDecimal(rs.getObject("requested_amount")),
                    rs.getString("currency_code"),
                    rs.getTimestamp("attempted_at"),
                    rs.getTimestamp("completed_at")
            );

    private static final RowMapper<PlatformBillingReconciliationItem> RECONCILIATION_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingReconciliationItem(
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getInt("legacy_subscription_id"),
                    rs.getDate("start_time"),
                    rs.getDate("end_time"),
                    getBigDecimal(rs.getObject("amount_to_pay")),
                    getBigDecimal(rs.getObject("amount_paid")),
                    (Integer) rs.getObject("legacy_order_id"),
                    rs.getString("legacy_status"),
                    (Long) rs.getObject("mirrored_branch_subscription_id"),
                    rs.getString("mirrored_branch_subscription_status"),
                    (Long) rs.getObject("mirrored_invoice_id"),
                    rs.getString("mirrored_invoice_status"),
                    (Long) rs.getObject("mirrored_payment_attempt_id"),
                    rs.getString("mirrored_payment_attempt_status"),
                    rs.getString("mirrored_provider_code"),
                    rs.getString("reconciliation_status")
            );

    private static final RowMapper<PlatformBillingPackageSummary> PACKAGE_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingPackageSummary(
                    rs.getString("package_id"),
                    rs.getString("package_display_name"),
                    rs.getInt("tenant_count"),
                    rs.getInt("active_subscriptions"),
                    rs.getInt("unpaid_subscriptions"),
                    getBigDecimal(rs.getObject("collected_amount")),
                    getBigDecimal(rs.getObject("outstanding_amount"))
            );

    private static final RowMapper<PlatformBillingSubscriptionItem> SUBSCRIPTION_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingSubscriptionItem(
                    rs.getInt("subscription_id"),
                    (Long) rs.getObject("billing_invoice_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("package_id"),
                    rs.getString("package_display_name"),
                    rs.getDate("start_time"),
                    rs.getDate("end_time"),
                    getBigDecimal(rs.getObject("amount_to_pay")),
                    getBigDecimal(rs.getObject("amount_paid")),
                    getBigDecimal(rs.getObject("outstanding_amount")),
                    rs.getString("status"),
                    rs.getBoolean("active")
            );

    private static final RowMapper<PlatformBillingDunningRunItem> DUNNING_RUN_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingDunningRunItem(
                    rs.getLong("billing_dunning_run_id"),
                    rs.getLong("billing_invoice_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("status"),
                    rs.getInt("attempt_number"),
                    rs.getTimestamp("scheduled_at"),
                    rs.getTimestamp("executed_at"),
                    rs.getString("result_summary")
            );

    private static final RowMapper<PlatformBillingRenewalBacklogItem> RENEWAL_BACKLOG_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingRenewalBacklogItem(
                    rs.getLong("previous_branch_subscription_id"),
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getString("price_code"),
                    rs.getString("billing_interval"),
                    getBigDecimal(rs.getObject("unit_amount")),
                    rs.getDate("current_period_start"),
                    rs.getDate("current_period_end")
            );

    private static final RowMapper<PlatformBillingEntitlementItem> ENTITLEMENT_ROW_MAPPER = (rs, rowNum) ->
            new PlatformBillingEntitlementItem(
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getLong("branch_subscription_id"),
                    rs.getLong("billing_invoice_id"),
                    rs.getString("current_state"),
                    rs.getString("event_type"),
                    rs.getString("reason_code"),
                    rs.getTimestamp("effective_at")
            );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbBillingAdminReadModels(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformBillingSummaryResponse getModernBillingSummary(String packageId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = "";
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause = " WHERE bbc.package_id = :packageId ";
        }

        String sql = latestBranchSubscriptionContextSql() +
                "SELECT " +
                " COUNT(*) FILTER (WHERE bbc.status = 'PD' AND bbc.end_time >= CURRENT_DATE) AS active_subscriptions, " +
                " COUNT(*) FILTER (WHERE bbc.status <> 'PD') AS unpaid_subscriptions, " +
                " COUNT(*) FILTER (WHERE bbc.status = 'PD' AND bbc.end_time < CURRENT_DATE) AS expired_paid_subscriptions, " +
                " COUNT(DISTINCT CASE WHEN bbc.status <> 'PD' THEN bbc.tenant_id END) AS tenants_with_unpaid_subscriptions, " +
                " COUNT(DISTINCT bbc.tenant_id) AS tenants_represented, " +
                " COALESCE(SUM(bbc.amount_paid), 0) AS collected_amount, " +
                " COALESCE(SUM(bbc.outstanding_amount), 0) AS outstanding_amount " +
                "FROM branch_billing_context bbc " +
                whereClause;

        Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, params);
        String normalizedPackageId = packageId == null || packageId.trim().isEmpty() ? null : packageId.trim();
        return new PlatformBillingSummaryResponse(
                normalizedPackageId,
                toInt(row.get("active_subscriptions")),
                toInt(row.get("unpaid_subscriptions")),
                toInt(row.get("expired_paid_subscriptions")),
                toInt(row.get("tenants_with_unpaid_subscriptions")),
                toInt(row.get("tenants_represented")),
                getBigDecimal(row.get("collected_amount")),
                getBigDecimal(row.get("outstanding_amount")),
                getModernPackageBreakdown(normalizedPackageId),
                new Timestamp(System.currentTimeMillis())
        );
    }

    public PlatformBillingHealthSnapshotResponse getBillingHealthSnapshot(Integer tenantId,
                                                                         int renewalLeadDays,
                                                                         int dunningGraceDays,
                                                                         int manualRetryCooldownMinutes,
                                                                         int manualRetryMaxAttempts) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("renewalCutoff", java.sql.Date.valueOf(LocalDate.now().plusDays(Math.max(0, renewalLeadDays))))
                .addValue("overdueThreshold", Timestamp.valueOf(LocalDateTime.now().minusDays(Math.max(0, dunningGraceDays))))
                .addValue("retryCooldownThreshold", Timestamp.valueOf(LocalDateTime.now().minusMinutes(Math.max(0, manualRetryCooldownMinutes))))
                .addValue("manualRetryMaxAttempts", Math.max(1, manualRetryMaxAttempts));

        String invoiceFilter = buildHealthTenantFilter("ias", tenantId, params);
        String renewalFilter = buildHealthTenantFilter("rbc", tenantId, params);
        String entitlementFilter = buildHealthTenantFilter("ec", tenantId, params);

        String sql = healthSnapshotContextSql() +
                "SELECT " +
                " COALESCE((SELECT COUNT(*) FROM invoice_attempt_summary ias " + invoiceFilter + " AND LOWER(ias.invoice_status) = 'open'), 0) AS open_invoices, " +
                " COALESCE((SELECT COUNT(*) FROM invoice_attempt_summary ias " + invoiceFilter + " AND LOWER(ias.invoice_status) = 'open' AND ias.due_at IS NOT NULL AND ias.due_at < :overdueThreshold), 0) AS overdue_invoices, " +
                " COALESCE((SELECT SUM(ias.due_amount) FROM invoice_attempt_summary ias " + invoiceFilter + " AND LOWER(ias.invoice_status) = 'open' AND ias.due_at IS NOT NULL AND ias.due_at < :overdueThreshold), 0) AS overdue_invoice_amount, " +
                " COALESCE((SELECT COUNT(*) FROM renewal_backlog_context rbc " + renewalFilter + "), 0) AS renewal_backlog_count, " +
                " COALESCE((SELECT COUNT(*) FROM entitlement_context ec " + entitlementFilter + " AND LOWER(ec.current_state) = 'pending_renewal'), 0) AS pending_renewal_entitlements, " +
                " COALESCE((SELECT COUNT(*) FROM entitlement_context ec " + entitlementFilter + " AND LOWER(ec.current_state) = 'past_due'), 0) AS past_due_entitlements, " +
                " COALESCE((SELECT COUNT(*) FROM invoice_attempt_summary ias " + invoiceFilter +
                " AND LOWER(ias.invoice_status) = 'open' AND COALESCE(ias.due_amount, 0) > 0 AND (" +
                " ias.attempt_count >= :manualRetryMaxAttempts OR " +
                " (ias.latest_attempt_at IS NOT NULL AND ias.latest_attempt_at > :retryCooldownThreshold)" +
                " )), 0) AS retry_blocked_invoices";

        Map<String, Object> row = namedParameterJdbcTemplate.queryForMap(sql, params);
        return new PlatformBillingHealthSnapshotResponse(
                tenantId,
                toInt(row.get("open_invoices")),
                toInt(row.get("overdue_invoices")),
                getBigDecimal(row.get("overdue_invoice_amount")),
                toInt(row.get("renewal_backlog_count")),
                toInt(row.get("pending_renewal_entitlements")),
                toInt(row.get("past_due_entitlements")),
                toInt(row.get("retry_blocked_invoices")),
                Math.max(0, manualRetryCooldownMinutes),
                Math.max(1, manualRetryMaxAttempts),
                new Timestamp(System.currentTimeMillis())
        );
    }

    public ArrayList<PlatformBillingPackageSummary> getModernPackageBreakdown(String packageId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = "";
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause = " WHERE bbc.package_id = :packageId ";
        }

        String sql = latestBranchSubscriptionContextSql() +
                "SELECT bbc.package_id, bbc.package_display_name, " +
                " COUNT(DISTINCT bbc.tenant_id) AS tenant_count, " +
                " COUNT(*) FILTER (WHERE bbc.status = 'PD' AND bbc.end_time >= CURRENT_DATE) AS active_subscriptions, " +
                " COUNT(*) FILTER (WHERE bbc.status <> 'PD') AS unpaid_subscriptions, " +
                " COALESCE(SUM(bbc.amount_paid), 0) AS collected_amount, " +
                " COALESCE(SUM(bbc.outstanding_amount), 0) AS outstanding_amount " +
                "FROM branch_billing_context bbc " +
                whereClause +
                "GROUP BY bbc.package_id, bbc.package_display_name " +
                "ORDER BY tenant_count DESC, bbc.package_id ASC";

        return new ArrayList<>(
                namedParameterJdbcTemplate.query(sql, params, PACKAGE_SUMMARY_ROW_MAPPER)
        );
    }

    public PlatformBillingSubscriptionsPageResponse getLatestModernSubscriptions(String search,
                                                                                 String status,
                                                                                 String packageId,
                                                                                 Integer tenantId,
                                                                                 int page,
                                                                                 int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        String whereClause = buildModernSubscriptionsWhereClause(search, status, packageId, tenantId, params);
        String baseSql = latestBranchSubscriptionContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM branch_billing_context bbc " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingSubscriptionItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT bbc.subscription_id, bbc.tenant_id, bbc.company_id, bbc.company_name, bbc.branch_id, bbc.branch_name, " +
                                "bbc.billing_invoice_id, " +
                                "bbc.package_id, bbc.package_display_name, bbc.start_time, bbc.end_time, bbc.amount_to_pay, bbc.amount_paid, " +
                                "bbc.outstanding_amount, bbc.status, bbc.active " +
                                "FROM branch_billing_context bbc " + whereClause +
                                " ORDER BY bbc.active DESC, bbc.end_time ASC NULLS LAST, bbc.company_name ASC, bbc.branch_name ASC " +
                                "LIMIT :limit OFFSET :offset",
                        params,
                        SUBSCRIPTION_ROW_MAPPER
                )
        );
        return new PlatformBillingSubscriptionsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformBillingInvoicesPageResponse getInvoices(String search,
                                                           String status,
                                                           String providerCode,
                                                           Integer tenantId,
                                                           int page,
                                                           int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        String whereClause = buildInvoicesWhereClause(search, status, providerCode, tenantId, params);
        String baseSql = invoiceContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM invoice_context ic " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingInvoiceItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT ic.* FROM invoice_context ic " + whereClause +
                                " ORDER BY ic.issued_at DESC NULLS LAST, ic.billing_invoice_id DESC LIMIT :limit OFFSET :offset",
                        params,
                        INVOICE_ROW_MAPPER
                )
        );
        return new PlatformBillingInvoicesPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformBillingPaymentAttemptsPageResponse getPaymentAttempts(String search,
                                                                         String status,
                                                                         String providerCode,
                                                                         Integer tenantId,
                                                                         int page,
                                                                         int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        String whereClause = buildPaymentAttemptsWhereClause(search, status, providerCode, tenantId, params);
        String baseSql = paymentAttemptContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM payment_attempt_context pac " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingPaymentAttemptItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT pac.* FROM payment_attempt_context pac " + whereClause +
                                " ORDER BY pac.attempted_at DESC, pac.billing_payment_attempt_id DESC LIMIT :limit OFFSET :offset",
                        params,
                        PAYMENT_ATTEMPT_ROW_MAPPER
                )
        );
        return new PlatformBillingPaymentAttemptsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformBillingDunningRunsPageResponse getDunningRuns(String status,
                                                                 Integer tenantId,
                                                                 int page,
                                                                 int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        String whereClause = buildDunningRunsWhereClause(status, tenantId, params);
        String baseSql = dunningRunContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM dunning_run_context drc " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingDunningRunItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT drc.* FROM dunning_run_context drc " + whereClause +
                                " ORDER BY drc.scheduled_at DESC, drc.billing_dunning_run_id DESC LIMIT :limit OFFSET :offset",
                        params,
                        DUNNING_RUN_ROW_MAPPER
                )
        );
        return new PlatformBillingDunningRunsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformBillingRenewalBacklogPageResponse getRenewalBacklog(Integer tenantId,
                                                                       int leadDays,
                                                                       int page,
                                                                       int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("renewalCutoff", Timestamp.valueOf(java.time.LocalDate.now().plusDays(Math.max(0, leadDays)).atStartOfDay()))
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);
        String whereClause = buildRenewalBacklogWhereClause(tenantId, params);
        String baseSql = renewalBacklogContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM renewal_backlog_context rbc " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingRenewalBacklogItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT rbc.* FROM renewal_backlog_context rbc " + whereClause +
                                " ORDER BY rbc.current_period_end ASC, rbc.company_name ASC, rbc.branch_name ASC LIMIT :limit OFFSET :offset",
                        params,
                        RENEWAL_BACKLOG_ROW_MAPPER
                )
        );
        return new PlatformBillingRenewalBacklogPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformBillingEntitlementsPageResponse getEntitlements(Integer tenantId,
                                                                   Integer branchId,
                                                                   String currentState,
                                                                   int page,
                                                                   int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);
        String whereClause = buildEntitlementsWhereClause(tenantId, branchId, currentState, params);
        String baseSql = entitlementContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM entitlement_context ec " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingEntitlementItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT ec.* FROM entitlement_context ec " + whereClause +
                                " ORDER BY ec.effective_at DESC, ec.branch_id ASC LIMIT :limit OFFSET :offset",
                        params,
                        ENTITLEMENT_ROW_MAPPER
                )
        );
        return new PlatformBillingEntitlementsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public PlatformBillingReconciliationPageResponse getReconciliation(String reconciliationStatus,
                                                                       Integer tenantId,
                                                                       int page,
                                                                       int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        String whereClause = buildReconciliationWhereClause(reconciliationStatus, tenantId, params);
        String baseSql = reconciliationContextSql();

        Integer total = namedParameterJdbcTemplate.queryForObject(
                baseSql + "SELECT COUNT(*) FROM reconciliation_context rc " + whereClause,
                params,
                Integer.class
        );
        long totalItems = total == null ? 0L : total.longValue();

        ArrayList<PlatformBillingReconciliationItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql +
                                "SELECT rc.* FROM reconciliation_context rc " + whereClause +
                                " ORDER BY rc.company_name ASC, rc.branch_name ASC LIMIT :limit OFFSET :offset",
                        params,
                        RECONCILIATION_ROW_MAPPER
                )
        );
        return new PlatformBillingReconciliationPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    public ArrayList<PlatformBillingReconciliationItem> getReconciliationCandidates(Integer tenantId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        String whereClause = buildReconciliationWhereClause(null, tenantId, params) +
                " AND rc.reconciliation_status <> 'synced' ";
        return new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        reconciliationContextSql() +
                                "SELECT rc.* FROM reconciliation_context rc " + whereClause +
                                " ORDER BY rc.company_name ASC, rc.branch_name ASC LIMIT :limit",
                        params,
                        RECONCILIATION_ROW_MAPPER
                )
        );
    }

    private String latestBranchSubscriptionContextSql() {
        return "WITH latest_payment_attempts AS (" +
                " SELECT DISTINCT ON (bpa.billing_invoice_id) " +
                " bpa.billing_invoice_id, bpa.provider_code, bpa.external_order_id, bpa.external_payment_reference, " +
                " bpa.status AS payment_attempt_status, bpa.attempted_at, bpa.completed_at " +
                " FROM public.billing_payment_attempts bpa " +
                " ORDER BY bpa.billing_invoice_id, bpa.billing_payment_attempt_id DESC" +
                "), latest_invoice_per_subscription AS (" +
                " SELECT DISTINCT ON (bi.source_id) " +
                " bi.source_id, bi.billing_invoice_id, bi.invoice_number, bi.status AS invoice_status, bi.total_amount, bi.due_amount, " +
                " bi.currency_code, bi.issued_at, bi.due_at, bi.paid_at, lpa.provider_code, lpa.external_order_id, " +
                " lpa.external_payment_reference, lpa.payment_attempt_status " +
                " FROM public.billing_invoices bi " +
                " LEFT JOIN latest_payment_attempts lpa ON lpa.billing_invoice_id = bi.billing_invoice_id " +
                " WHERE bi.source_type = 'branch_subscription' " +
                " ORDER BY bi.source_id, bi.billing_invoice_id DESC" +
                "), branch_billing_context AS (" +
                " SELECT DISTINCT ON (b.\"branchId\") " +
                " t.tenant_id, c.id AS company_id, c.\"companyName\" AS company_name, " +
                " b.\"branchId\" AS branch_id, b.\"branchName\" AS branch_name, " +
                " t.package_id, COALESCE(pp.display_name, t.package_id) AS package_display_name, " +
                " COALESCE(bs.branch_subscription_id, 0) AS subscription_id, " +
                " lis.billing_invoice_id, " +
                " bs.current_period_start AS start_time, bs.current_period_end AS end_time, " +
                " COALESCE(lis.total_amount, bs.unit_amount, 0) AS amount_to_pay, " +
                " GREATEST(COALESCE(lis.total_amount, bs.unit_amount, 0) - COALESCE(lis.due_amount, 0), 0) AS amount_paid, " +
                " CASE " +
                "   WHEN bs.branch_subscription_id IS NULL THEN 0 " +
                "   ELSE COALESCE(lis.due_amount, bs.unit_amount, 0) " +
                " END AS outstanding_amount, " +
                " COALESCE(lis.provider_code, '') AS provider_code, " +
                " COALESCE(lis.external_order_id, '') AS external_order_id, " +
                " COALESCE(lis.invoice_status, CASE WHEN bs.branch_subscription_id IS NULL THEN 'unpaid' ELSE 'open' END) AS invoice_status, " +
                " COALESCE(bs.status, 'pending_payment') AS branch_subscription_status, " +
                " CASE " +
                "   WHEN bs.branch_subscription_id IS NOT NULL " +
                "    AND LOWER(COALESCE(bs.status, '')) = 'active' " +
                "    AND LOWER(COALESCE(lis.invoice_status, '')) = 'paid' THEN 'PD' " +
                "   ELSE 'UP' " +
                " END AS status, " +
                " CASE " +
                "   WHEN bs.branch_subscription_id IS NOT NULL " +
                "    AND LOWER(COALESCE(bs.status, '')) = 'active' " +
                "    AND LOWER(COALESCE(lis.invoice_status, '')) = 'paid' " +
                "    AND bs.current_period_end >= CURRENT_DATE THEN TRUE " +
                "   ELSE FALSE " +
                " END AS active " +
                " FROM public.\"Branch\" b " +
                " JOIN public.tenants t ON t.tenant_id = b.\"companyId\" " +
                " JOIN public.\"Company\" c ON c.id = b.\"companyId\" " +
                " LEFT JOIN public.package_plans pp ON pp.package_id = t.package_id " +
                " LEFT JOIN public.branch_subscriptions bs ON bs.branch_id = b.\"branchId\" " +
                " LEFT JOIN latest_invoice_per_subscription lis ON lis.source_id = bs.branch_subscription_id::text " +
                " ORDER BY b.\"branchId\", bs.branch_subscription_id DESC NULLS LAST, lis.billing_invoice_id DESC NULLS LAST" +
                ") ";
    }

    private String invoiceContextSql() {
        return "WITH invoice_context AS (" +
                " SELECT bi.billing_invoice_id, ba.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                " bs.branch_id, b.\"branchName\" AS branch_name, bs.branch_subscription_id, bi.invoice_number, bi.status, " +
                " bi.total_amount, bi.due_amount, bi.currency_code, bi.source_type, bi.source_id, bi.issued_at, bi.due_at, bi.paid_at, " +
                " COALESCE(pa.provider_code, '') AS provider_code " +
                " FROM public.billing_invoices bi " +
                " JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                " LEFT JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " LEFT JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                " LEFT JOIN LATERAL (" +
                "   SELECT provider_code FROM public.billing_payment_attempts pa " +
                "   WHERE pa.billing_invoice_id = bi.billing_invoice_id " +
                "   ORDER BY pa.billing_payment_attempt_id DESC LIMIT 1" +
                " ) pa ON TRUE" +
                ") ";
    }

    private String paymentAttemptContextSql() {
        return "WITH payment_attempt_context AS (" +
                " SELECT bpa.billing_payment_attempt_id, bpa.billing_invoice_id, ba.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                " bs.branch_id, b.\"branchName\" AS branch_name, bpa.provider_code, bpa.external_order_id, bpa.external_payment_reference, " +
                " bpa.status, bpa.requested_amount, bpa.currency_code, bpa.attempted_at, bpa.completed_at " +
                " FROM public.billing_payment_attempts bpa " +
                " JOIN public.billing_invoices bi ON bi.billing_invoice_id = bpa.billing_invoice_id " +
                " JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                " LEFT JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " LEFT JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                ") ";
    }

    private String dunningRunContextSql() {
        return "WITH dunning_run_context AS (" +
                " SELECT bdr.billing_dunning_run_id, bdr.billing_invoice_id, ba.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                " bs.branch_id, b.\"branchName\" AS branch_name, bdr.status, bdr.attempt_number, bdr.scheduled_at, bdr.executed_at, bdr.result_summary " +
                " FROM public.billing_dunning_runs bdr " +
                " JOIN public.billing_accounts ba ON ba.billing_account_id = bdr.billing_account_id " +
                " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                " LEFT JOIN public.billing_invoices bi ON bi.billing_invoice_id = bdr.billing_invoice_id " +
                " LEFT JOIN public.branch_subscriptions bs ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " LEFT JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                ") ";
    }

    private String renewalBacklogContextSql() {
        return "WITH latest_paid AS (" +
                " SELECT DISTINCT ON (bs.branch_id) " +
                " bs.branch_subscription_id AS previous_branch_subscription_id, bs.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                " bs.branch_id, b.\"branchName\" AS branch_name, bs.price_code, bs.billing_interval, bs.unit_amount, bs.current_period_start, bs.current_period_end " +
                " FROM public.branch_subscriptions bs " +
                " JOIN public.billing_accounts ba ON ba.billing_account_id = bs.billing_account_id " +
                " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                " JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                " JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " WHERE LOWER(bs.status) = 'active' AND LOWER(bi.status) = 'paid' " +
                " ORDER BY bs.branch_id, bs.current_period_end DESC, bs.branch_subscription_id DESC" +
                "), renewal_backlog_context AS (" +
                " SELECT lp.* " +
                " FROM latest_paid lp " +
                " WHERE lp.current_period_end <= :renewalCutoff " +
                " AND NOT EXISTS (" +
                "   SELECT 1 FROM public.branch_subscriptions future_bs " +
                "   WHERE future_bs.branch_id = lp.branch_id AND future_bs.current_period_start > lp.current_period_end" +
                " )" +
                ") ";
    }

    private String entitlementContextSql() {
        return "WITH latest_entitlements AS (" +
                " SELECT DISTINCT ON (bee.branch_id) " +
                " bee.branch_id, COALESCE(bee.branch_subscription_id, 0) AS branch_subscription_id, COALESCE(bee.billing_invoice_id, 0) AS billing_invoice_id, " +
                " bee.to_state AS current_state, bee.event_type, bee.reason_code, bee.effective_at " +
                " FROM public.billing_entitlement_events bee " +
                " ORDER BY bee.branch_id, bee.effective_at DESC, bee.billing_entitlement_event_id DESC" +
                "), entitlement_context AS (" +
                " SELECT ba.tenant_id, ba.company_id, c.\"companyName\" AS company_name, le.branch_id, b.\"branchName\" AS branch_name, " +
                " le.branch_subscription_id, le.billing_invoice_id, le.current_state, le.event_type, le.reason_code, le.effective_at " +
                " FROM latest_entitlements le " +
                " JOIN public.\"Branch\" b ON b.\"branchId\" = le.branch_id " +
                " JOIN public.billing_accounts ba ON ba.company_id = b.\"companyId\" " +
                " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                ") ";
    }

    private String reconciliationContextSql() {
        return "WITH latest_legacy AS (" +
                " SELECT DISTINCT ON (cs.\"branchId\") " +
                " b.\"companyId\" AS tenant_id, c.id AS company_id, c.\"companyName\" AS company_name, " +
                " b.\"branchId\" AS branch_id, b.\"branchName\" AS branch_name, cs.\"sId\" AS legacy_subscription_id, " +
                " cs.\"startTime\" AS start_time, cs.\"endTime\" AS end_time, " +
                " cs.\"amountToPay\"::money::numeric AS amount_to_pay, cs.\"amountPaid\"::money::numeric AS amount_paid, " +
                " cs.order_id AS legacy_order_id, cs.status AS legacy_status " +
                " FROM public.\"CompanySubscription\" cs " +
                " JOIN public.\"Branch\" b ON b.\"branchId\" = cs.\"branchId\" " +
                " JOIN public.\"Company\" c ON c.id = b.\"companyId\" " +
                " ORDER BY cs.\"branchId\", cs.\"sId\" DESC" +
                "), latest_attempts AS (" +
                " SELECT DISTINCT ON (bi.source_id) bi.source_id, bpa.billing_payment_attempt_id, bpa.status, bpa.provider_code " +
                " FROM public.billing_invoices bi " +
                " LEFT JOIN public.billing_payment_attempts bpa ON bpa.billing_invoice_id = bi.billing_invoice_id " +
                " WHERE bi.source_type = 'branch_subscription' " +
                " ORDER BY bi.source_id, bpa.billing_payment_attempt_id DESC" +
                "), reconciliation_context AS (" +
                " SELECT ll.tenant_id, ll.company_id, ll.company_name, ll.branch_id, ll.branch_name, ll.legacy_subscription_id, ll.start_time, ll.end_time, " +
                " ll.amount_to_pay, ll.amount_paid, ll.legacy_order_id, ll.legacy_status, " +
                " bs.branch_subscription_id AS mirrored_branch_subscription_id, bs.status AS mirrored_branch_subscription_status, " +
                " bi.billing_invoice_id AS mirrored_invoice_id, bi.status AS mirrored_invoice_status, " +
                " la.billing_payment_attempt_id AS mirrored_payment_attempt_id, la.status AS mirrored_payment_attempt_status, la.provider_code AS mirrored_provider_code, " +
                " CASE " +
                "   WHEN bs.branch_subscription_id IS NULL THEN 'missing_branch_subscription' " +
                "   WHEN bi.billing_invoice_id IS NULL THEN 'missing_invoice' " +
                "   WHEN COALESCE(ll.legacy_order_id, 0) > 0 AND la.billing_payment_attempt_id IS NULL THEN 'missing_payment_attempt' " +
                "   WHEN ll.legacy_status = 'PD' AND (COALESCE(bs.status, '') <> 'active' OR COALESCE(bi.status, '') <> 'paid' OR COALESCE(la.status, '') <> 'succeeded') THEN 'status_mismatch' " +
                "   WHEN ll.legacy_status <> 'PD' AND (COALESCE(bs.status, '') = 'active' OR COALESCE(bi.status, '') = 'paid') THEN 'status_mismatch' " +
                "   WHEN COALESCE(ll.amount_to_pay, 0) <> COALESCE(bi.total_amount, 0) OR COALESCE(ll.amount_paid, 0) <> (COALESCE(bi.total_amount, 0) - COALESCE(bi.due_amount, 0)) THEN 'amount_mismatch' " +
                "   ELSE 'synced' " +
                " END AS reconciliation_status " +
                " FROM latest_legacy ll " +
                " LEFT JOIN public.branch_subscriptions bs ON bs.legacy_subscription_id = ll.legacy_subscription_id " +
                " LEFT JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " LEFT JOIN latest_attempts la ON la.source_id = bs.branch_subscription_id::text " +
                ") ";
    }

    private String healthSnapshotContextSql() {
        return "WITH invoice_attempt_summary AS (" +
                " SELECT bi.billing_invoice_id, ba.tenant_id, bi.status AS invoice_status, bi.due_amount, bi.due_at, " +
                " COUNT(bpa.billing_payment_attempt_id) AS attempt_count, " +
                " MAX(COALESCE(bpa.completed_at, bpa.attempted_at)) AS latest_attempt_at " +
                " FROM public.billing_invoices bi " +
                " JOIN public.billing_accounts ba ON ba.billing_account_id = bi.billing_account_id " +
                " LEFT JOIN public.billing_payment_attempts bpa ON bpa.billing_invoice_id = bi.billing_invoice_id " +
                " WHERE bi.source_type = 'branch_subscription' " +
                " GROUP BY bi.billing_invoice_id, ba.tenant_id, bi.status, bi.due_amount, bi.due_at" +
                "), latest_paid AS (" +
                " SELECT DISTINCT ON (bs.branch_id) " +
                " bs.branch_subscription_id AS previous_branch_subscription_id, bs.tenant_id, ba.company_id, c.\"companyName\" AS company_name, " +
                " bs.branch_id, b.\"branchName\" AS branch_name, bs.price_code, bs.billing_interval, bs.unit_amount, bs.current_period_start, bs.current_period_end " +
                " FROM public.branch_subscriptions bs " +
                " JOIN public.billing_accounts ba ON ba.billing_account_id = bs.billing_account_id " +
                " JOIN public.\"Company\" c ON c.id = ba.company_id " +
                " JOIN public.\"Branch\" b ON b.\"branchId\" = bs.branch_id " +
                " JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " WHERE LOWER(bs.status) = 'active' AND LOWER(bi.status) = 'paid' " +
                " ORDER BY bs.branch_id, bs.current_period_end DESC, bs.branch_subscription_id DESC" +
                "), renewal_backlog_context AS (" +
                " SELECT lp.* " +
                " FROM latest_paid lp " +
                " WHERE lp.current_period_end <= :renewalCutoff " +
                " AND NOT EXISTS (" +
                "   SELECT 1 FROM public.branch_subscriptions future_bs " +
                "   WHERE future_bs.branch_id = lp.branch_id AND future_bs.current_period_start > lp.current_period_end" +
                " )" +
                "), latest_entitlements AS (" +
                " SELECT DISTINCT ON (bee.branch_id) " +
                " bee.branch_id, bee.to_state AS current_state " +
                " FROM public.billing_entitlement_events bee " +
                " ORDER BY bee.branch_id, bee.effective_at DESC, bee.billing_entitlement_event_id DESC" +
                "), entitlement_context AS (" +
                " SELECT ba.tenant_id, le.branch_id, le.current_state " +
                " FROM latest_entitlements le " +
                " JOIN public.\"Branch\" b ON b.\"branchId\" = le.branch_id " +
                " JOIN public.billing_accounts ba ON ba.company_id = b.\"companyId\"" +
                ") ";
    }

    private String buildInvoicesWhereClause(String search,
                                            String status,
                                            String providerCode,
                                            Integer tenantId,
                                            MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (search != null && !search.trim().isEmpty()) {
            params.addValue("search", "%" + search.trim() + "%");
            whereClause.append(" AND (ic.company_name ILIKE :search OR ic.branch_name ILIKE :search OR ic.invoice_number ILIKE :search) ");
        }
        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim());
            whereClause.append(" AND ic.status = :status ");
        }
        if (providerCode != null && !providerCode.trim().isEmpty()) {
            params.addValue("providerCode", providerCode.trim());
            whereClause.append(" AND LOWER(ic.provider_code) = LOWER(:providerCode) ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND ic.tenant_id = :tenantId ");
        }
        return whereClause.toString();
    }

    private String buildModernSubscriptionsWhereClause(String search,
                                                       String status,
                                                       String packageId,
                                                       Integer tenantId,
                                                       MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (search != null && !search.trim().isEmpty()) {
            params.addValue("search", "%" + search.trim() + "%");
            whereClause.append(" AND (bbc.company_name ILIKE :search OR bbc.branch_name ILIKE :search) ");
        }
        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim().toUpperCase());
            whereClause.append(" AND UPPER(bbc.status) = :status ");
        }
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause.append(" AND bbc.package_id = :packageId ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND bbc.tenant_id = :tenantId ");
        }
        return whereClause.toString();
    }

    private String buildPaymentAttemptsWhereClause(String search,
                                                   String status,
                                                   String providerCode,
                                                   Integer tenantId,
                                                   MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (search != null && !search.trim().isEmpty()) {
            params.addValue("search", "%" + search.trim() + "%");
            whereClause.append(" AND (pac.company_name ILIKE :search OR pac.branch_name ILIKE :search OR pac.external_order_id ILIKE :search) ");
        }
        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim());
            whereClause.append(" AND pac.status = :status ");
        }
        if (providerCode != null && !providerCode.trim().isEmpty()) {
            params.addValue("providerCode", providerCode.trim());
            whereClause.append(" AND LOWER(pac.provider_code) = LOWER(:providerCode) ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND pac.tenant_id = :tenantId ");
        }
        return whereClause.toString();
    }

    private String buildDunningRunsWhereClause(String status,
                                               Integer tenantId,
                                               MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim());
            whereClause.append(" AND drc.status = :status ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND drc.tenant_id = :tenantId ");
        }
        return whereClause.toString();
    }

    private String buildRenewalBacklogWhereClause(Integer tenantId,
                                                  MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND rbc.tenant_id = :tenantId ");
        }
        return whereClause.toString();
    }

    private String buildEntitlementsWhereClause(Integer tenantId,
                                                Integer branchId,
                                                String currentState,
                                                MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND ec.tenant_id = :tenantId ");
        }
        if (branchId != null) {
            params.addValue("branchId", branchId);
            whereClause.append(" AND ec.branch_id = :branchId ");
        }
        if (currentState != null && !currentState.trim().isEmpty()) {
            params.addValue("currentState", currentState.trim());
            whereClause.append(" AND ec.current_state = :currentState ");
        }
        return whereClause.toString();
    }

    private String buildReconciliationWhereClause(String reconciliationStatus,
                                                  Integer tenantId,
                                                  MapSqlParameterSource params) {
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (reconciliationStatus != null && !reconciliationStatus.trim().isEmpty()) {
            params.addValue("reconciliationStatus", reconciliationStatus.trim());
            whereClause.append(" AND rc.reconciliation_status = :reconciliationStatus ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND rc.tenant_id = :tenantId ");
        }
        return whereClause.toString();
    }

    private String buildHealthTenantFilter(String alias,
                                           Integer tenantId,
                                           MapSqlParameterSource params) {
        if (tenantId == null) {
            return " WHERE 1=1 ";
        }
        params.addValue("tenantId", tenantId);
        return " WHERE " + alias + ".tenant_id = :tenantId ";
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private int computeTotalPages(long totalItems, int size) {
        if (totalItems == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / (double) size);
    }

    private static BigDecimal getBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
