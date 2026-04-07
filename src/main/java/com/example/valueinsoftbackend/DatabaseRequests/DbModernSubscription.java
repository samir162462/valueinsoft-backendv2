package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbModernSubscription {

    private static final RowMapper<AppModelSubscription> SUBSCRIPTION_ROW_MAPPER = (rs, rowNum) ->
            new AppModelSubscription(
                    rs.getInt("subscription_id"),
                    rs.getDate("start_time"),
                    rs.getDate("end_time"),
                    rs.getInt("branch_id"),
                    getBigDecimal(rs.getObject("amount_to_pay")),
                    getBigDecimal(rs.getObject("amount_paid")),
                    rs.getInt("order_id"),
                    rs.getString("legacy_status")
            );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbModernSubscription(JdbcTemplate jdbcTemplate,
                                NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<AppModelSubscription> getBranchSubscriptions(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String sql = modernSubscriptionContextSql() +
                "SELECT mc.subscription_id, mc.start_time, mc.end_time, mc.branch_id, mc.amount_to_pay, mc.amount_paid, mc.order_id, mc.legacy_status " +
                "FROM modern_context mc WHERE mc.branch_id = :branchId ORDER BY mc.subscription_id DESC";
        return namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource().addValue("branchId", branchId),
                SUBSCRIPTION_ROW_MAPPER
        );
    }

    public boolean hasBranchSubscriptionRecords(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        Boolean exists = namedParameterJdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM public.branch_subscriptions WHERE branch_id = :branchId)",
                new MapSqlParameterSource().addValue("branchId", branchId),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public Map<String, Object> getBranchActiveState(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String sql = modernSubscriptionContextSql() +
                "SELECT mc.subscription_id, mc.start_time, mc.end_time, mc.amount_to_pay, mc.amount_paid, mc.order_id, mc.legacy_status " +
                "FROM modern_context mc WHERE mc.branch_id = :branchId " +
                "ORDER BY mc.subscription_id DESC LIMIT 1";
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource().addValue("branchId", branchId)
        );
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.get(0);
        Date startTime = (Date) row.get("start_time");
        Date endTime = (Date) row.get("end_time");
        LocalDate currentDate = LocalDate.now();
        long allTime = ChronoUnit.DAYS.between(startTime.toLocalDate(), endTime.toLocalDate());
        long remaining = ChronoUnit.DAYS.between(currentDate, endTime.toLocalDate());
        boolean active = remaining > 0 && "PD".equals(row.get("legacy_status"));

        Map<String, Object> details = new HashMap<>();
        details.put("sDate", startTime);
        details.put("eDate", endTime);
        details.put("cDate", Date.valueOf(currentDate));
        details.put("allTime", (int) allTime);
        details.put("remainingTime", (int) remaining);
        details.put("status", row.get("legacy_status"));
        details.put("active", active);
        return details;
    }

    public Integer getBranchIdByExternalOrderId(String providerCode, String externalOrderId) {
        List<Integer> ids = namedParameterJdbcTemplate.query(
                "SELECT bs.branch_id " +
                        "FROM public.branch_subscriptions bs " +
                        "JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                        "JOIN public.billing_payment_attempts bpa ON bpa.billing_invoice_id = bi.billing_invoice_id " +
                        "WHERE LOWER(bpa.provider_code) = LOWER(:providerCode) AND bpa.external_order_id = :externalOrderId " +
                        "ORDER BY bs.branch_subscription_id DESC",
                new MapSqlParameterSource()
                        .addValue("providerCode", providerCode)
                        .addValue("externalOrderId", externalOrderId),
                (rs, rowNum) -> rs.getInt("branch_id")
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long createBranchSubscription(long billingAccountId,
                                         int tenantId,
                                         int branchId,
                                         String priceCode,
                                         BigDecimal unitAmount,
                                         Date startDate,
                                         Date endDate) {
        String sql = "INSERT INTO public.branch_subscriptions " +
                "(billing_account_id, branch_id, tenant_id, price_code, status, unit_amount, start_date, current_period_start, current_period_end, metadata_json) " +
                "VALUES (:billingAccountId, :branchId, :tenantId, :priceCode, :status, :unitAmount, :startDate, :currentPeriodStart, :currentPeriodEnd, CAST(:metadataJson AS jsonb))";
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("billingAccountId", billingAccountId)
                        .addValue("branchId", branchId)
                        .addValue("tenantId", tenantId)
                        .addValue("priceCode", priceCode)
                        .addValue("status", "pending_payment")
                        .addValue("unitAmount", unitAmount)
                        .addValue("startDate", startDate)
                        .addValue("currentPeriodStart", startDate)
                        .addValue("currentPeriodEnd", endDate)
                        .addValue("metadataJson", "{\"source\":\"modern_subscription_service\"}"),
                keyHolder,
                new String[]{"branch_subscription_id"}
        );
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BRANCH_SUBSCRIPTION_CREATE_FAILED", "Branch subscription could not be created");
        }
        return key.longValue();
    }

    private String modernSubscriptionContextSql() {
        return "WITH latest_attempts AS (" +
                " SELECT DISTINCT ON (bi.source_id) bi.source_id, bpa.external_order_id, bpa.status AS payment_attempt_status " +
                " FROM public.billing_invoices bi " +
                " LEFT JOIN public.billing_payment_attempts bpa ON bpa.billing_invoice_id = bi.billing_invoice_id " +
                " WHERE bi.source_type = 'branch_subscription' " +
                " ORDER BY bi.source_id, bpa.billing_payment_attempt_id DESC" +
                "), modern_context AS (" +
                " SELECT bs.branch_subscription_id AS subscription_id, bs.branch_id, bs.current_period_start AS start_time, bs.current_period_end AS end_time, " +
                " bi.total_amount AS amount_to_pay, (bi.total_amount - bi.due_amount) AS amount_paid, " +
                " CASE " +
                "   WHEN la.external_order_id ~ '^[0-9]{1,9}$' THEN CAST(la.external_order_id AS INTEGER) " +
                "   WHEN la.external_order_id ~ '^[0-9]{10}$' AND la.external_order_id <= '2147483647' THEN CAST(la.external_order_id AS INTEGER) " +
                "   ELSE 0 " +
                " END AS order_id, " +
                " CASE WHEN bi.status = 'paid' AND bs.status = 'active' THEN 'PD' ELSE 'NP' END AS legacy_status " +
                " FROM public.branch_subscriptions bs " +
                " LEFT JOIN public.billing_invoices bi ON bi.source_type = 'branch_subscription' AND bi.source_id = bs.branch_subscription_id::text " +
                " LEFT JOIN latest_attempts la ON la.source_id = bs.branch_subscription_id::text" +
                ") ";
    }

    private static BigDecimal getBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
