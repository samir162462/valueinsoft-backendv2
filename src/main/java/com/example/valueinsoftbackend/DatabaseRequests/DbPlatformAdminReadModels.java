package com.example.valueinsoftbackend.DatabaseRequests;

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
                    rs.getString("latest_subscription_status")
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

    public DbPlatformAdminReadModels(JdbcTemplate jdbcTemplate,
                                     NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformOverviewResponse getOverview() {
        String sql = "WITH latest_subscriptions AS (" +
                " SELECT DISTINCT ON (\"branchId\") \"branchId\", status, \"endTime\" " +
                " FROM public.\"CompanySubscription\" " +
                " ORDER BY \"branchId\", \"sId\" DESC" +
                ") " +
                "SELECT " +
                " (SELECT COUNT(*) FROM public.\"Company\") AS total_companies, " +
                " (SELECT COUNT(*) FROM public.tenants WHERE status = 'active') AS active_companies, " +
                " (SELECT COUNT(*) FROM public.tenants WHERE status = 'suspended') AS suspended_companies, " +
                " (SELECT COUNT(*) FROM public.\"Branch\") AS total_branches, " +
                " (SELECT COUNT(*) FROM public.branch_runtime_states WHERE status = 'active') AS active_branches, " +
                " (SELECT COUNT(*) FROM public.branch_runtime_states WHERE status = 'locked') AS locked_branches, " +
                " (SELECT COUNT(*) FROM public.onboarding_states WHERE status IN ('in_progress', 'blocked', 'failed_recovery_required')) AS tenants_in_onboarding, " +
                " (SELECT COUNT(*) FROM latest_subscriptions WHERE COALESCE(status, 'NP') <> 'PD') AS unpaid_subscriptions, " +
                " (SELECT COUNT(*) FROM latest_subscriptions WHERE status = 'PD' AND \"endTime\" >= CURRENT_DATE) AS active_subscriptions";

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
                " WITH latest_subscriptions AS (" +
                "   SELECT DISTINCT ON (\"branchId\") \"branchId\", status " +
                "   FROM public.\"CompanySubscription\" " +
                "   ORDER BY \"branchId\", \"sId\" DESC" +
                " ) " +
                " SELECT b.\"companyId\" AS company_id, COUNT(*) FILTER (WHERE COALESCE(ls.status, 'NP') <> 'PD') AS unpaid_branch_subscriptions " +
                " FROM public.\"Branch\" b " +
                " LEFT JOIN latest_subscriptions ls ON ls.\"branchId\" = b.\"branchId\" " +
                " GROUP BY b.\"companyId\"" +
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
                " WITH latest_subscriptions AS (" +
                "   SELECT DISTINCT ON (\"branchId\") \"branchId\", status " +
                "   FROM public.\"CompanySubscription\" " +
                "   ORDER BY \"branchId\", \"sId\" DESC" +
                " ) " +
                " SELECT b.\"companyId\" AS company_id, COUNT(*) FILTER (WHERE COALESCE(ls.status, 'NP') <> 'PD') AS unpaid_branch_subscriptions " +
                " FROM public.\"Branch\" b " +
                " LEFT JOIN latest_subscriptions ls ON ls.\"branchId\" = b.\"branchId\" " +
                " GROUP BY b.\"companyId\"" +
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
                getCompanyBranches(tenantId)
        );
    }

    public ArrayList<PlatformCompanyBranchSummary> getCompanyBranches(int tenantId) {
        String sql = "WITH latest_subscriptions AS (" +
                " SELECT DISTINCT ON (\"branchId\") \"branchId\", status " +
                " FROM public.\"CompanySubscription\" " +
                " ORDER BY \"branchId\", \"sId\" DESC" +
                "), user_counts AS (" +
                " SELECT \"branchId\", COUNT(*) AS user_count FROM public.users GROUP BY \"branchId\"" +
                ") " +
                "SELECT " +
                " b.\"branchId\" AS branch_id, b.\"companyId\" AS tenant_id, b.\"branchName\" AS branch_name, " +
                " b.\"branchLocation\" AS branch_location, b.\"branchEstTime\" AS branch_established_time, " +
                " COALESCE(brs.status, 'active') AS runtime_status, COALESCE(uc.user_count, 0) AS user_count, " +
                " COALESCE(ls.status, 'NONE') AS latest_subscription_status " +
                "FROM public.\"Branch\" b " +
                "LEFT JOIN public.branch_runtime_states brs ON brs.branch_id = b.\"branchId\" " +
                "LEFT JOIN user_counts uc ON uc.\"branchId\" = b.\"branchId\" " +
                "LEFT JOIN latest_subscriptions ls ON ls.\"branchId\" = b.\"branchId\" " +
                "WHERE b.\"companyId\" = ? ORDER BY b.\"branchName\" ASC";
        return new ArrayList<>(jdbcTemplate.query(sql, COMPANY_BRANCH_ROW_MAPPER, tenantId));
    }

    public ArrayList<PlatformCompanySubscriptionItem> getCompanySubscriptions(int tenantId) {
        String sql = "WITH latest_subscriptions AS (" +
                " SELECT DISTINCT ON (cs.\"branchId\") " +
                " cs.\"sId\" AS subscription_id, cs.\"branchId\" AS branch_id, cs.\"startTime\" AS start_time, " +
                " cs.\"endTime\" AS end_time, cs.\"amountToPay\"::money::numeric AS amount_to_pay, " +
                " cs.\"amountPaid\"::money::numeric AS amount_paid, cs.order_id, cs.status " +
                " FROM public.\"CompanySubscription\" cs " +
                " JOIN public.\"Branch\" b ON b.\"branchId\" = cs.\"branchId\" " +
                " WHERE b.\"companyId\" = ? " +
                " ORDER BY cs.\"branchId\", cs.\"sId\" DESC" +
                ") " +
                "SELECT ls.subscription_id, ls.branch_id, b.\"branchName\" AS branch_name, ls.start_time, ls.end_time, " +
                "ls.amount_to_pay, ls.amount_paid, ls.order_id, ls.status " +
                "FROM latest_subscriptions ls " +
                "JOIN public.\"Branch\" b ON b.\"branchId\" = ls.branch_id " +
                "ORDER BY b.\"branchName\" ASC";
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
