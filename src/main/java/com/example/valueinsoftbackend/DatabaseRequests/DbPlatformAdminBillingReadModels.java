package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionsPageResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingPackageSummary;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;

@Repository
public class DbPlatformAdminBillingReadModels {

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
                    null,
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

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbPlatformAdminBillingReadModels(JdbcTemplate jdbcTemplate,
                                            NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformBillingSummaryResponse getBillingSummary(String packageId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = "";
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause = " WHERE t.package_id = :packageId ";
        }

        String summarySql =
                "WITH latest_subscriptions AS (" +
                        " SELECT DISTINCT ON (cs.\"branchId\") " +
                        " cs.\"branchId\" AS branch_id, cs.status, " +
                        " cs.\"amountToPay\"::money::numeric AS amount_to_pay, " +
                        " cs.\"amountPaid\"::money::numeric AS amount_paid, " +
                        " cs.\"endTime\" AS end_time " +
                        " FROM public.\"CompanySubscription\" cs " +
                        " ORDER BY cs.\"branchId\", cs.\"sId\" DESC" +
                        "), subscription_context AS (" +
                        " SELECT b.\"companyId\" AS tenant_id, b.\"branchId\" AS branch_id, " +
                        " COALESCE(ls.status, 'NP') AS status, " +
                        " COALESCE(ls.amount_to_pay, 0) AS amount_to_pay, " +
                        " COALESCE(ls.amount_paid, 0) AS amount_paid, " +
                        " GREATEST(COALESCE(ls.amount_to_pay, 0) - COALESCE(ls.amount_paid, 0), 0) AS outstanding_amount, " +
                        " ls.end_time " +
                        " FROM public.\"Branch\" b " +
                        " LEFT JOIN latest_subscriptions ls ON ls.branch_id = b.\"branchId\"" +
                        ") " +
                        "SELECT " +
                        " COUNT(*) FILTER (WHERE sc.status = 'PD' AND sc.end_time >= CURRENT_DATE) AS active_subscriptions, " +
                        " COUNT(*) FILTER (WHERE sc.status <> 'PD') AS unpaid_subscriptions, " +
                        " COUNT(*) FILTER (WHERE sc.status = 'PD' AND sc.end_time < CURRENT_DATE) AS expired_paid_subscriptions, " +
                        " COUNT(DISTINCT CASE WHEN sc.status <> 'PD' THEN sc.tenant_id END) AS tenants_with_unpaid_subscriptions, " +
                        " COUNT(DISTINCT sc.tenant_id) AS tenants_represented, " +
                        " COALESCE(SUM(sc.amount_paid), 0) AS collected_amount, " +
                        " COALESCE(SUM(sc.outstanding_amount), 0) AS outstanding_amount " +
                        "FROM subscription_context sc " +
                        "JOIN public.tenants t ON t.tenant_id = sc.tenant_id " +
                        whereClause;

        Map<String, Object> row = whereClause.isEmpty()
                ? jdbcTemplate.queryForMap(summarySql)
                : namedParameterJdbcTemplate.queryForMap(summarySql, params);

        return new PlatformBillingSummaryResponse(
                packageId == null || packageId.trim().isEmpty() ? null : packageId.trim(),
                toInt(row.get("active_subscriptions")),
                toInt(row.get("unpaid_subscriptions")),
                toInt(row.get("expired_paid_subscriptions")),
                toInt(row.get("tenants_with_unpaid_subscriptions")),
                toInt(row.get("tenants_represented")),
                getBigDecimal(row.get("collected_amount")),
                getBigDecimal(row.get("outstanding_amount")),
                getPackageBreakdown(packageId),
                new Timestamp(System.currentTimeMillis())
        );
    }

    public ArrayList<PlatformBillingPackageSummary> getPackageBreakdown(String packageId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = "";
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause = " WHERE t.package_id = :packageId ";
        }

        String sql =
                "WITH latest_subscriptions AS (" +
                        " SELECT DISTINCT ON (cs.\"branchId\") " +
                        " cs.\"branchId\" AS branch_id, cs.status, " +
                        " cs.\"amountToPay\"::money::numeric AS amount_to_pay, " +
                        " cs.\"amountPaid\"::money::numeric AS amount_paid, " +
                        " cs.\"endTime\" AS end_time " +
                        " FROM public.\"CompanySubscription\" cs " +
                        " ORDER BY cs.\"branchId\", cs.\"sId\" DESC" +
                        "), subscription_context AS (" +
                        " SELECT b.\"companyId\" AS tenant_id, b.\"branchId\" AS branch_id, " +
                        " COALESCE(ls.status, 'NP') AS status, " +
                        " COALESCE(ls.amount_paid, 0) AS amount_paid, " +
                        " GREATEST(COALESCE(ls.amount_to_pay, 0) - COALESCE(ls.amount_paid, 0), 0) AS outstanding_amount, " +
                        " ls.end_time " +
                        " FROM public.\"Branch\" b " +
                        " LEFT JOIN latest_subscriptions ls ON ls.branch_id = b.\"branchId\"" +
                        ") " +
                        "SELECT t.package_id, COALESCE(pp.display_name, t.package_id) AS package_display_name, " +
                        " COUNT(DISTINCT t.tenant_id) AS tenant_count, " +
                        " COUNT(*) FILTER (WHERE sc.status = 'PD' AND sc.end_time >= CURRENT_DATE) AS active_subscriptions, " +
                        " COUNT(*) FILTER (WHERE sc.status <> 'PD') AS unpaid_subscriptions, " +
                        " COALESCE(SUM(sc.amount_paid), 0) AS collected_amount, " +
                        " COALESCE(SUM(sc.outstanding_amount), 0) AS outstanding_amount " +
                        "FROM public.tenants t " +
                        "JOIN subscription_context sc ON sc.tenant_id = t.tenant_id " +
                        "LEFT JOIN public.package_plans pp ON pp.package_id = t.package_id " +
                        whereClause +
                        "GROUP BY t.package_id, COALESCE(pp.display_name, t.package_id) " +
                        "ORDER BY tenant_count DESC, t.package_id ASC";

        return new ArrayList<>(
                whereClause.isEmpty()
                        ? jdbcTemplate.query(sql, PACKAGE_SUMMARY_ROW_MAPPER)
                        : namedParameterJdbcTemplate.query(sql, params, PACKAGE_SUMMARY_ROW_MAPPER)
        );
    }

    public PlatformBillingSubscriptionsPageResponse getLatestSubscriptions(String search,
                                                                           String status,
                                                                           String packageId,
                                                                           Integer tenantId,
                                                                           int page,
                                                                           int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (page - 1) * size);

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (search != null && !search.trim().isEmpty()) {
            params.addValue("search", "%" + search.trim() + "%");
            whereClause.append(" AND (sc.company_name ILIKE :search OR sc.branch_name ILIKE :search) ");
        }
        if (status != null && !status.trim().isEmpty()) {
            params.addValue("status", status.trim());
            whereClause.append(" AND sc.status = :status ");
        }
        if (packageId != null && !packageId.trim().isEmpty()) {
            params.addValue("packageId", packageId.trim());
            whereClause.append(" AND sc.package_id = :packageId ");
        }
        if (tenantId != null) {
            params.addValue("tenantId", tenantId);
            whereClause.append(" AND sc.tenant_id = :tenantId ");
        }

        String baseSql =
                "WITH latest_subscriptions AS (" +
                        " SELECT DISTINCT ON (cs.\"branchId\") " +
                        " cs.\"sId\" AS subscription_id, cs.\"branchId\" AS branch_id, cs.\"startTime\" AS start_time, " +
                        " cs.\"endTime\" AS end_time, cs.status, " +
                        " cs.\"amountToPay\"::money::numeric AS amount_to_pay, " +
                        " cs.\"amountPaid\"::money::numeric AS amount_paid " +
                        " FROM public.\"CompanySubscription\" cs " +
                        " ORDER BY cs.\"branchId\", cs.\"sId\" DESC" +
                        "), subscription_context AS (" +
                        " SELECT ls.subscription_id, b.\"companyId\" AS tenant_id, b.\"companyId\" AS company_id, " +
                        " c.\"CompanyName\" AS company_name, b.\"branchId\" AS branch_id, b.\"branchName\" AS branch_name, " +
                        " t.package_id, COALESCE(pp.display_name, t.package_id) AS package_display_name, " +
                        " ls.start_time, ls.end_time, COALESCE(ls.status, 'NP') AS status, " +
                        " COALESCE(ls.amount_to_pay, 0) AS amount_to_pay, COALESCE(ls.amount_paid, 0) AS amount_paid, " +
                        " GREATEST(COALESCE(ls.amount_to_pay, 0) - COALESCE(ls.amount_paid, 0), 0) AS outstanding_amount, " +
                        " CASE WHEN COALESCE(ls.status, 'NP') = 'PD' AND ls.end_time >= CURRENT_DATE THEN true ELSE false END AS active " +
                        " FROM public.\"Branch\" b " +
                        " JOIN public.\"Company\" c ON c.\"CompanyID\" = b.\"companyId\" " +
                        " JOIN public.tenants t ON t.tenant_id = b.\"companyId\" " +
                        " LEFT JOIN public.package_plans pp ON pp.package_id = t.package_id " +
                        " LEFT JOIN latest_subscriptions ls ON ls.branch_id = b.\"branchId\"" +
                        ") ";

        String countSql = baseSql + "SELECT COUNT(*) FROM subscription_context sc " + whereClause;
        String dataSql = baseSql +
                "SELECT sc.subscription_id, sc.tenant_id, sc.company_id, sc.company_name, sc.branch_id, sc.branch_name, " +
                "sc.package_id, sc.package_display_name, sc.start_time, sc.end_time, sc.amount_to_pay, sc.amount_paid, " +
                "sc.outstanding_amount, sc.status, sc.active " +
                "FROM subscription_context sc " + whereClause +
                "ORDER BY sc.active DESC, sc.end_time ASC NULLS LAST, sc.company_name ASC, sc.branch_name ASC " +
                "LIMIT :limit OFFSET :offset";

        Integer total = namedParameterJdbcTemplate.queryForObject(countSql, params, Integer.class);
        long totalItems = total == null ? 0L : total.longValue();
        ArrayList<PlatformBillingSubscriptionItem> items = new ArrayList<>(
                namedParameterJdbcTemplate.query(dataSql, params, SUBSCRIPTION_ROW_MAPPER)
        );
        return new PlatformBillingSubscriptionsPageResponse(items, page, size, totalItems, computeTotalPages(totalItems, size));
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static BigDecimal getBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private int computeTotalPages(long totalItems, int size) {
        if (totalItems == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / (double) size);
    }
}
