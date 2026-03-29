package com.example.valueinsoftbackend.DatabaseRequests.DbApp;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.AppModel.AppModelSubscription;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbSubscription {

    private static final RowMapper<AppModelSubscription> subscriptionRowMapper = (rs, rowNum) -> new AppModelSubscription(
            rs.getInt("sId"),
            rs.getDate("startTime"),
            rs.getDate("endTime"),
            rs.getInt("branchId"),
            rs.getBigDecimal("amountToPay"),
            rs.getBigDecimal("amountPaid"),
            rs.getInt("order_id"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbSubscription(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<AppModelSubscription> getBranchSubscription(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String sql = "SELECT \"sId\", \"startTime\", \"endTime\", \"branchId\", " +
                "\"amountToPay\"::money::numeric AS \"amountToPay\", " +
                "\"amountPaid\"::money::numeric AS \"amountPaid\", order_id, status " +
                "FROM " + TenantSqlIdentifiers.companySubscriptionTable() +
                " WHERE \"branchId\" = ? ORDER BY \"sId\" ASC";
        return jdbcTemplate.query(sql, subscriptionRowMapper, branchId);
    }

    public int createBranchSubscription(AppModelSubscription appModelSubscription) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.companySubscriptionTable() +
                " (\"startTime\", \"endTime\", \"branchId\", \"amountToPay\", \"amountPaid\", order_id, status) " +
                "VALUES (:startTime, :endTime, :branchId, :amountToPay, :amountPaid, :orderId, :status)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startTime", appModelSubscription.getStartTime())
                .addValue("endTime", appModelSubscription.getEndTime())
                .addValue("branchId", appModelSubscription.getBranchId())
                .addValue("amountToPay", appModelSubscription.getAmountToPay())
                .addValue("amountPaid", appModelSubscription.getAmountPaid())
                .addValue("orderId", appModelSubscription.getOrder_id())
                .addValue("status", appModelSubscription.getStatus());
        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[]{"sId"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUBSCRIPTION_INSERT_FAILED", "Subscription could not be created");
        }
        return key.intValue();
    }

    public void updateBranchSubscriptionWithPayMobId(int orderId, int subscriptionId) {
        int rows = jdbcTemplate.update(
                "UPDATE " + TenantSqlIdentifiers.companySubscriptionTable() + " SET order_id = ? WHERE \"sId\" = ?",
                orderId,
                subscriptionId
        );
        if (rows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "Subscription not found");
        }
    }

    public void markBranchSubscriptionPaid(int orderId) {
        int rows = jdbcTemplate.update(
                "UPDATE " + TenantSqlIdentifiers.companySubscriptionTable() +
                        " SET status = 'PD', \"amountPaid\" = COALESCE(\"amountToPay\", \"amountPaid\") " +
                        "WHERE order_id = ? AND status <> 'PD'",
                orderId
        );
        if (rows == 0 && !existsByOrderId(orderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_ORDER_NOT_FOUND", "Subscription order not found");
        }
    }

    public boolean existsByOrderId(int orderId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + TenantSqlIdentifiers.companySubscriptionTable() + " WHERE order_id = ?)",
                Boolean.class,
                orderId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Map<String, Object> isActive(int branchId) {
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        String sql = "SELECT \"startTime\", \"endTime\", CURRENT_DATE AS currentDate, " +
                "\"endTime\" - \"startTime\" AS allTime, \"endTime\" - CURRENT_DATE AS remaining, status, " +
                "CASE WHEN \"endTime\" - CURRENT_DATE > 0 THEN true ELSE false END AS active " +
                "FROM " + TenantSqlIdentifiers.companySubscriptionTable() +
                " WHERE \"branchId\" = ? ORDER BY \"sId\" DESC LIMIT 1";

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> details = new HashMap<>();
            details.put("sDate", rs.getDate("startTime"));
            details.put("eDate", rs.getDate("endTime"));
            details.put("cDate", rs.getDate("currentDate"));
            details.put("allTime", rs.getInt("allTime"));
            details.put("remainingTime", rs.getInt("remaining"));
            details.put("status", rs.getString("status"));
            details.put("active", rs.getBoolean("active"));
            return details;
        }, branchId);

        return rows.isEmpty() ? null : rows.get(0);
    }

    public AppModelSubscription toSubscription(Date startTime, Date endTime, int branchId,
                                               java.math.BigDecimal amountToPay, java.math.BigDecimal amountPaid,
                                               int orderId, String status) {
        return new AppModelSubscription(0, startTime, endTime, branchId, amountToPay, amountPaid, orderId, status);
    }
}
