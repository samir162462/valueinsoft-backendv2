package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Config.BillingProperties;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanySubscriptionItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompaniesPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompany360Response;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanyBranchSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformCompanyListItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewPackageSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformOverviewResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class DbPlatformAdminReadModels {

    private static final RowMapper<PlatformOverviewPackageSummary> PACKAGE_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new PlatformOverviewPackageSummary(
                    rs.getString("package_id"),
                    rs.getString("package_display_name"),
                    rs.getInt("tenant_count")
            );

    private static final RowMapper<PlatformCompanyListItem> COMPANY_LIST_ROW_MAPPER = (rs, rowNum) ->
            new PlatformCompanyListItem(
                    rs.getInt("tenant_id"),
                    rs.getInt("company_id"),
                    rs.getString("company_name"),
                    rs.getInt("owner_id"),
                    rs.getString("package_id"),
                    rs.getString("package_display_name"),
                    rs.getString("template_id"),
                    rs.getString("template_display_name"),
                    rs.getString("tenant_status"),
                    rs.getTimestamp("created_at"),
                    rs.getInt("branch_count"),
                    rs.getInt("user_count"),
                    rs.getInt("unpaid_branch_subscriptions")
            );

    private static final RowMapper<PlatformCompanyBranchSummary> COMPANY_BRANCH_ROW_MAPPER = (rs, rowNum) ->
            new PlatformCompanyBranchSummary(
                    rs.getInt("branch_id"),
                    rs.getInt("tenant_id"),
                    rs.getString("branch_name"),
                    rs.getString("branch_location"),
                    rs.getTimestamp("branch_established_time"),
                    rs.getString("runtime_status"),
                    rs.getInt("user_count"),
                    rs.getString("latest_subscription_status"),
                    rs.getString("current_entitlement_state"),
                    rs.getInt("overdue_invoice_count"),
                    rs.getBoolean("retry_blocked")
            );

    private static final RowMapper<PlatformCompanySubscriptionItem> COMPANY_SUBSCRIPTION_ROW_MAPPER = (rs, rowNum) ->
            new PlatformCompanySubscriptionItem(
                    rs.getInt("subscription_id"),
                    rs.getInt("branch_id"),
                    rs.getString("branch_name"),
                    rs.getDate("start_time"),
                    rs.getDate("end_time"),
                    getBigDecimal(rs.getObject("amount_to_pay")),
                    getBigDecimal(rs.getObject("amount_paid")),
                    rs.getInt("order_id"),
                    rs.getString("status")
            );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final BillingProperties billingProperties;

    public DbPlatformAdminReadModels(JdbcTemplate jdbcTemplate,
                                     NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                     BillingProperties billingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.billingProperties = billingProperties;
    }

    public PlatformOverviewResponse getOverview() {
        String sql = latestBranchSubscriptionContextSql() +
                "SELECT " +
                " (SELECT COUNT(*) FROM public.\"Company\") AS total_companies, " +
                " (SELECT COUNT(*) FROM public.tenants WHERE status = 'active') AS active_companies, " +
                " (SELECT COUNT(*) FROM public.tenants WHERE status = 'suspended') AS suspended_companies, " +
                " (SELECT COUNT(*) FROM public.\"Branch\") AS total_branches, " +
                " (SELECT COUNT(*) FROM public.branch_runtime_states WHERE status = 'active') AS active_branches, " +
                " (SELECT COUNT(*) FROM public.branch_runtime_states WHERE status = 'locked') AS locked_branches, " +
                " (SELECT COUNT(*) FROM public.onboarding_states WHERE status IN ('in_progress', 'blocked', 'failed_recovery_required')) AS tenants_in_onboarding, " +
                " (SELECT COUNT(*) FROM branch_billing_context WHERE status <> 'PD') AS unpaid_subscriptions, " +
                " (SELECT COUNT(*) FROM branch_billing_context WHERE status = 'PD' AND end_time >= CURRENT_DATE) AS active_subscriptions";

        Map<String, Object> row = jdbcTemplate.queryForMap(sql);

        return new PlatformOverviewResponse(
                toInt(row.get("total_companies")),
                toInt(row.get("active_companies")),
                toInt(row.get("suspended_companies")),
                toInt(row.get("total_branches")),
                toInt(row.get("active_branches")),
                toInt(row.get("locked_branches")),
                toInt(row.get("tenants_in_onboarding")),
                toInt(row.get("unpaid_subscriptions")),
                toInt(row.get("active_subscriptions")),
                getPackageDistribution(),
                null,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new Timestamp(System.currentTimeMillis())
        );
    }

    public ArrayList<PlatformOverviewPackageSummary> getPackageDistribution() {
        String sql = "SELECT t.package_id, COALESCE(pp.display_name, t.package_id) AS package_display_name, COUNT(*) AS tenant_count " +
                "FROM public.tenants t " +
                "LEFT JOIN public.package_plans pp ON pp.package_id = t.package_id " +
                "GROUP BY t.package_id, COALESCE(pp.display_name, t.package_id) " +
                "ORDER BY tenant_count DESC, t.package_id ASC";
        return new ArrayList<>(jdbcTemplate.query(sql, PACKAGE_SUMMARY_ROW_MAPPER));
    }

    public PlatformCompaniesPageResponse getCompanies(String search,
                                                      String status,
                                                      String packageId,
                                                      String templateId,
                                                      int page,
                                                      int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", normalizedSize)
                .addValue("offset", offset);

        String whereClause = buildCompanyWhereClause(search, status, packageId, templateId, params);

        String baseSql = "FROM public.tenants t " +
                "JOIN public.\"Company\" c ON c.id = t.tenant_id " +
                "LEFT JOIN public.package_plans pp ON pp.package_id = t.package_id " +
                "LEFT JOIN public.company_templates ct ON ct.template_id = t.template_id " +
                "LEFT JOIN (" +
                " SELECT b.\"companyId\" AS company_id, COUNT(*) AS branch_count " +
                " FROM public.\"Branch\" b GROUP BY b.\"companyId\"" +
                ") branches ON branches.company_id = t.tenant_id " +
                "LEFT JOIN (" +
                " SELECT b.\"companyId\" AS company_id, COUNT(u.id) AS user_count " +
                " FROM public.users u JOIN public.\"Branch\" b ON b.\"branchId\" = u.\"branchId\" " +
                " GROUP BY b.\"companyId\"" +
                ") users ON users.company_id = t.tenant_id " +
                "LEFT JOIN (" +
                latestBranchSubscriptionContextSql() +
                " SELECT tenant_id AS company_id, COUNT(*) FILTER (WHERE status <> 'PD') AS unpaid_branch_subscriptions " +
                " FROM branch_billing_context " +
                " GROUP BY tenant_id" +
                ") subscriptions ON subscriptions.company_id = t.tenant_id " +
                whereClause;

        String countSql = "SELECT COUNT(*) " + baseSql;
        Long totalItemsValue = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);
        long totalItems = totalItemsValue == null ? 0 : totalItemsValue;

        String listSql = "SELECT " +
                " t.tenant_id, c.id AS company_id, c.\"companyName\" AS company_name, c.\"ownerId\" AS owner_id, " +
                " t.package_id, COALESCE(pp.display_name, t.package_id) AS package_display_name, " +
                " t.template_id, COALESCE(ct.display_name, t.template_id) AS template_display_name, " +
                " t.status AS tenant_status, c.\"establishedTime\" AS created_at, " +
                " COALESCE(branches.branch_count, 0) AS branch_count, " +
                " COALESCE(users.user_count, 0) AS user_count, " +
                " COALESCE(subscriptions.unpaid_branch_subscriptions, 0) AS unpaid_branch_subscriptions " +
                baseSql +
                " ORDER BY c.\"establishedTime\" DESC, c.id DESC LIMIT :limit OFFSET :offset";

        ArrayList<PlatformCompanyListItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(listSql, params, COMPANY_LIST_ROW_MAPPER)
        );

        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);
        return new PlatformCompaniesPageResponse(items, normalizedPage, normalizedSize, totalItems, totalPages);
    }

    public PlatformCompany360Response getCompany360(int tenantId) {
        String sql = "SELECT " +
                " t.tenant_id, c.id AS company_id, c.\"companyName\" AS company_name, c.\"ownerId\" AS owner_id, " +
                " t.status AS tenant_status, t.package_id, COALESCE(pp.display_name, t.package_id) AS package_display_name, " +
                " t.template_id, COALESCE(ct.display_name, t.template_id) AS template_display_name, " +
                " COALESCE(os.status, 'not_started') AS onboarding_status, c.\"establishedTime\" AS created_at, " +
                " COALESCE(branch_stats.branch_count, 0) AS branch_count, " +
                " COALESCE(branch_stats.active_branch_count, 0) AS active_branch_count, " +
                " COALESCE(branch_stats.locked_branch_count, 0) AS locked_branch_count, " +
                " COALESCE(user_stats.user_count, 0) AS user_count, " +
                " COALESCE(subscription_stats.unpaid_branch_subscriptions, 0) AS unpaid_branch_subscriptions " +
                "FROM public.tenants t " +
                "JOIN public.\"Company\" c ON c.id = t.tenant_id " +
                "LEFT JOIN public.package_plans pp ON pp.package_id = t.package_id " +
                "LEFT JOIN public.company_templates ct ON ct.template_id = t.template_id " +
                "LEFT JOIN public.onboarding_states os ON os.tenant_id = t.tenant_id " +
                "LEFT JOIN (" +
                " SELECT b.\"companyId\" AS company_id, COUNT(*) AS branch_count, " +
                " COUNT(*) FILTER (WHERE COALESCE(brs.status, 'active') = 'active') AS active_branch_count, " +
                " COUNT(*) FILTER (WHERE COALESCE(brs.status, 'active') = 'locked') AS locked_branch_count " +
                " FROM public.\"Branch\" b " +
                " LEFT JOIN public.branch_runtime_states brs ON brs.branch_id = b.\"branchId\" " +
                " GROUP BY b.\"companyId\"" +
                ") branch_stats ON branch_stats.company_id = t.tenant_id " +
                "LEFT JOIN (" +
                " SELECT b.\"companyId\" AS company_id, COUNT(u.id) AS user_count " +
                " FROM public.users u JOIN public.\"Branch\" b ON b.\"branchId\" = u.\"branchId\" " +
                " GROUP BY b.\"companyId\"" +
                ") user_stats ON user_stats.company_id = t.tenant_id " +
                "LEFT JOIN (" +
                latestBranchSubscriptionContextSql() +
                " SELECT tenant_id AS company_id, COUNT(*) FILTER (WHERE status <> 'PD') AS unpaid_branch_subscriptions " +
                " FROM branch_billing_context " +
                " GROUP BY tenant_id" +
                ") subscription_stats ON subscription_stats.company_id = t.tenant_id " +
                "WHERE t.tenant_id = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }

        Map<String, Object> row = rows.get(0);
        return new PlatformCompany360Response(
                toInt(row.get("tenant_id")),
                toInt(row.get("company_id")),
                stringValue(row.get("company_name")),
                toInt(row.get("owner_id")),
                stringValue(row.get("tenant_status")),
                stringValue(row.get("package_id")),
                stringValue(row.get("package_display_name")),
                stringValue(row.get("template_id")),
                stringValue(row.get("template_display_name")),
                stringValue(row.get("onboarding_status")),
                (Timestamp) row.get("created_at"),
                toInt(row.get("branch_count")),
                toInt(row.get("active_branch_count")),
                toInt(row.get("locked_branch_count")),
                toInt(row.get("user_count")),
                toInt(row.get("unpaid_branch_subscriptions")),
                null,
                getCompanyBranches(tenantId)
        );
    }

    public ArrayList<PlatformCompanyBranchSummary> getCompanyBranches(int tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("manualRetryMaxAttempts", Math.max(1, billingProperties.getManualRetryMaxAttempts()))
                .addValue("retryCooldownThreshold", Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(Math.max(0, billingProperties.getManualRetryCooldownMinutes()))));
        String sql = companyBranchBillingContextSql() +
                "SELECT " +
                " b.\"branchId\" AS branch_id, b.\"companyId\" AS tenant_id, b.\"branchName\" AS branch_name, " +
                " b.\"branchLocation\" AS branch_location, b.\"branchEstTime\" AS branch_established_time, " +
                " COALESCE(brs.status, 'active') AS runtime_status, COALESCE(uc.user_count, 0) AS user_count, " +
                " COALESCE(bbc.status, 'UP') AS latest_subscription_status, " +
                " COALESCE(le.current_state, 'unknown') AS current_entitlement_state, " +
                " COALESCE(ias.overdue_invoice_count, 0) AS overdue_invoice_count, " +
                " COALESCE(ias.retry_blocked, FALSE) AS retry_blocked " +
                "FROM public.\"Branch\" b " +
                "LEFT JOIN public.branch_runtime_states brs ON brs.branch_id = b.\"branchId\" " +
                "LEFT JOIN (" +
                " SELECT \"branchId\", COUNT(*) AS user_count FROM public.users GROUP BY \"branchId\"" +
                ") uc ON uc.\"branchId\" = b.\"branchId\" " +
                "LEFT JOIN branch_billing_context bbc ON bbc.branch_id = b.\"branchId\" " +
                "LEFT JOIN latest_entitlements le ON le.branch_id = b.\"branchId\" " +
                "LEFT JOIN invoice_attempt_summary ias ON ias.branch_id = b.\"branchId\" " +
                "WHERE b.\"companyId\" = :tenantId ORDER BY b.\"branchName\" ASC";
        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, COMPANY_BRANCH_ROW_MAPPER));
    }

    public ArrayList<PlatformCompanySubscriptionItem> getCompanySubscriptions(int tenantId) {
        String sql = latestBranchSubscriptionContextSql() +
                "SELECT bbc.subscription_id, bbc.branch_id, bbc.branch_name, bbc.start_time, bbc.end_time, " +
                "bbc.amount_to_pay, bbc.amount_paid, " +
                "CASE " +
                " WHEN bbc.external_order_id ~ '^[0-9]{1,9}$' THEN CAST(bbc.external_order_id AS INTEGER) " +
                " WHEN bbc.external_order_id ~ '^[0-9]{10}$' AND bbc.external_order_id <= '2147483647' THEN CAST(bbc.external_order_id AS INTEGER) " +
                " ELSE 0 " +
                "END AS order_id, " +
                "bbc.status " +
                "FROM branch_billing_context bbc " +
                "WHERE bbc.tenant_id = ? " +
                "ORDER BY bbc.branch_name ASC";
        return new ArrayList<>(jdbcTemplate.query(sql, COMPANY_SUBSCRIPTION_ROW_MAPPER, tenantId));
    }

    public boolean tenantExists(int tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.tenants WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        return count != null && count > 0;
    }

    public Integer getBranchTenantId(int branchId) {
        List<Integer> results = jdbcTemplate.query(
                "SELECT \"companyId\" FROM public.\"Branch\" WHERE \"branchId\" = ?",
                (rs, rowNum) -> (Integer) rs.getObject("companyId"),
                branchId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private String latestBranchSubscriptionContextSql() {
        return "WITH latest_payment_attempts AS (" +
                " SELECT DISTINCT ON (bpa.billing_invoice_id) " +
                " bpa.billing_invoice_id, bpa.provider_code, bpa.external_order_id " +
                " FROM public.billing_payment_attempts bpa " +
                " ORDER BY bpa.billing_invoice_id, bpa.billing_payment_attempt_id DESC" +
                "), latest_invoice_per_subscription AS (" +
                " SELECT DISTINCT ON (bi.source_id) " +
                " bi.source_id, bi.billing_invoice_id, bi.status AS invoice_status, bi.total_amount, bi.due_amount, " +
                " lpa.provider_code, lpa.external_order_id " +
                " FROM public.billing_invoices bi " +
                " LEFT JOIN latest_payment_attempts lpa ON lpa.billing_invoice_id = bi.billing_invoice_id " +
                " WHERE bi.source_type = 'branch_subscription' " +
                " ORDER BY bi.source_id, bi.billing_invoice_id DESC" +
                "), branch_billing_context AS (" +
                " SELECT DISTINCT ON (b.\"branchId\") " +
                " t.tenant_id, c.id AS company_id, c.\"companyName\" AS company_name, " +
                " b.\"branchId\" AS branch_id, b.\"branchName\" AS branch_name, " +
                " COALESCE(bs.branch_subscription_id, 0) AS subscription_id, " +
                " bs.current_period_start AS start_time, bs.current_period_end AS end_time, " +
                " COALESCE(lis.total_amount, bs.unit_amount, 0) AS amount_to_pay, " +
                " GREATEST(COALESCE(lis.total_amount, bs.unit_amount, 0) - COALESCE(lis.due_amount, 0), 0) AS amount_paid, " +
                " COALESCE(lis.external_order_id, '') AS external_order_id, " +
                " CASE " +
                "   WHEN bs.branch_subscription_id IS NOT NULL " +
                "    AND LOWER(COALESCE(bs.status, '')) = 'active' " +
                "    AND LOWER(COALESCE(lis.invoice_status, '')) = 'paid' THEN 'PD' " +
                "   ELSE 'UP' " +
                " END AS status " +
                " FROM public.\"Branch\" b " +
                " JOIN public.tenants t ON t.tenant_id = b.\"companyId\" " +
                " JOIN public.\"Company\" c ON c.id = b.\"companyId\" " +
                " LEFT JOIN public.branch_subscriptions bs ON bs.branch_id = b.\"branchId\" " +
                " LEFT JOIN latest_invoice_per_subscription lis ON lis.source_id = bs.branch_subscription_id::text " +
                " ORDER BY b.\"branchId\", bs.branch_subscription_id DESC NULLS LAST, lis.billing_invoice_id DESC NULLS LAST" +
                ") ";
    }

    private String companyBranchBillingContextSql() {
        return latestBranchSubscriptionContextSql() +
                ", latest_entitlements AS (" +
                " SELECT DISTINCT ON (bee.branch_id) bee.branch_id, bee.to_state AS current_state " +
                " FROM public.billing_entitlement_events bee " +
                " ORDER BY bee.branch_id, bee.effective_at DESC, bee.billing_entitlement_event_id DESC" +
                "), invoice_attempt_summary AS (" +
                " SELECT bs.branch_id, " +
                " COUNT(*) FILTER (WHERE LOWER(bi.status) = 'open' AND bi.due_at IS NOT NULL AND bi.due_at < NOW()) AS overdue_invoice_count, " +
                " BOOL_OR( " +
                "   LOWER(bi.status) = 'open' AND COALESCE(bi.due_amount, 0) > 0 AND (" +
                "     COALESCE(pa.attempt_count, 0) >= :manualRetryMaxAttempts OR " +
                "     (pa.latest_attempt_at IS NOT NULL AND pa.latest_attempt_at > :retryCooldownThreshold)" +
                "   )" +
                " ) AS retry_blocked " +
                " FROM public.branch_subscriptions bs " +
                " LEFT JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " LEFT JOIN (" +
                "   SELECT billing_invoice_id, COUNT(*) AS attempt_count, MAX(COALESCE(completed_at, attempted_at)) AS latest_attempt_at " +
                "   FROM public.billing_payment_attempts GROUP BY billing_invoice_id" +
                " ) pa ON pa.billing_invoice_id = bi.billing_invoice_id " +
                " GROUP BY bs.branch_id" +
                ") ";
    }

    private String buildCompanyWhereClause(String search,
                                           String status,
                                           String packageId,
                                           String templateId,
                                           MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (search != null && !search.trim().isEmpty()) {
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
            where.append(" AND (LOWER(c.\"companyName\") LIKE :search OR CAST(t.tenant_id AS TEXT) LIKE :search) ");
        }

        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim().toLowerCase());
            where.append(" AND LOWER(t.status) = :status ");
        }

        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            where.append(" AND t.package_id = :packageId ");
        }

        if (templateId != null && !templateId.trim().isEmpty()) {
            params.addValue("templateId", templateId.trim());
            where.append(" AND t.template_id = :templateId ");
        }

        return where.toString();
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal getBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }
}
