package com.example.valueinsoftbackend.customerbehavior.repository;

import com.example.valueinsoftbackend.customerbehavior.config.CustomerBehaviorMetricRules;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerPreferenceItem;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerProductAffinity;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRecentOrder;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerRetentionCohort;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerSpendTrendPoint;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;

@Repository
public class CustomerBehaviorRepository {

    private static final int DEFAULT_LIMIT = 25;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerBehaviorRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CustomerBehaviorMetricRecord> findCustomerMetrics(CustomerBehaviorQueryScope scope) {
        String sql = """
                WITH orders_to_date AS (
                    %s
                ),
                period_orders AS (
                    SELECT *
                    FROM orders_to_date
                    WHERE order_time >= :fromTime
                ),
                detail_lines AS (
                    %s
                ),
                order_metrics AS (
                    SELECT customer_id,
                           COUNT(*) AS period_order_count,
                           COALESCE(SUM(gross_total), 0)::numeric AS gross_spend,
                           COALESCE(SUM(discount_total), 0)::numeric AS discount_total,
                           COALESCE(SUM(return_total), 0)::numeric AS return_total,
                           COALESCE(SUM(net_total), 0)::numeric AS net_spend
                    FROM period_orders
                    GROUP BY customer_id
                ),
                historical_metrics AS (
                    SELECT customer_id,
                           COUNT(*) AS historical_order_count,
                           MIN(order_time) AS first_purchase_at,
                           MAX(order_time) AS last_purchase_at
                    FROM orders_to_date
                    GROUP BY customer_id
                ),
                line_metrics AS (
                    SELECT customer_id,
                           COALESCE(SUM(CASE WHEN bounced_back = 0 THEN GREATEST(quantity, 0) ELSE 0 END), 0)::numeric AS purchased_quantity
                    FROM detail_lines
                    GROUP BY customer_id
                ),
                product_rank AS (
                    SELECT customer_id,
                           product_name,
                           ROW_NUMBER() OVER (
                               PARTITION BY customer_id
                               ORDER BY SUM(CASE WHEN bounced_back = 0 THEN quantity ELSE 0 END) DESC,
                                        SUM(CASE WHEN bounced_back = 0 THEN line_total ELSE 0 END) DESC,
                                        product_name ASC
                           ) AS rn
                    FROM detail_lines
                    WHERE bounced_back = 0
                    GROUP BY customer_id, product_name
                ),
                category_spend AS (
                    SELECT customer_id,
                           category_name,
                           COALESCE(SUM(CASE WHEN bounced_back = 0 THEN line_total ELSE 0 END), 0)::numeric AS category_spend
                    FROM detail_lines
                    WHERE bounced_back = 0
                    GROUP BY customer_id, category_name
                ),
                category_rank AS (
                    SELECT customer_id,
                           category_name,
                           category_spend,
                           SUM(category_spend) OVER (PARTITION BY customer_id) AS total_category_spend,
                           ROW_NUMBER() OVER (
                               PARTITION BY customer_id
                               ORDER BY category_spend DESC, category_name ASC
                           ) AS rn
                    FROM category_spend
                )
                SELECT c.c_id,
                       c."clientName",
                       c."clientPhone",
                       c."branchId",
                       b."branchName",
                       c."registeredTime",
                       COALESCE(om.period_order_count, 0) AS period_order_count,
                       COALESCE(hm.historical_order_count, 0) AS historical_order_count,
                       COALESCE(om.gross_spend, 0)::numeric AS gross_spend,
                       COALESCE(om.discount_total, 0)::numeric AS discount_total,
                       COALESCE(om.return_total, 0)::numeric AS return_total,
                       COALESCE(om.net_spend, 0)::numeric AS net_spend,
                       COALESCE(lm.purchased_quantity, 0)::numeric AS purchased_quantity,
                       hm.first_purchase_at,
                       hm.last_purchase_at,
                       pr.product_name AS favorite_product,
                       cr.category_name AS favorite_category,
                       COALESCE(cr.category_spend, 0)::numeric AS favorite_category_spend,
                       COALESCE(cr.total_category_spend, 0)::numeric AS total_category_spend
                FROM %s c
                LEFT JOIN public."Branch" b ON b."branchId" = c."branchId"
                LEFT JOIN order_metrics om ON om.customer_id = c.c_id
                LEFT JOIN historical_metrics hm ON hm.customer_id = c.c_id
                LEFT JOIN line_metrics lm ON lm.customer_id = c.c_id
                LEFT JOIN product_rank pr ON pr.customer_id = c.c_id AND pr.rn = 1
                LEFT JOIN category_rank cr ON cr.customer_id = c.c_id AND cr.rn = 1
                WHERE c."branchId" IN (:branchIds)
                   OR om.customer_id IS NOT NULL
                   OR hm.customer_id IS NOT NULL
                ORDER BY c.c_id DESC
                """.formatted(
                orderUnionSql(scope),
                detailLinesUnionSql(scope),
                TenantSqlIdentifiers.clientTable(Math.toIntExact(scope.companyId()))
        );
        return jdbcTemplate.query(sql, params(scope), this::mapMetricRecord);
    }

    public List<CustomerPreferenceItem> findTopProducts(CustomerBehaviorQueryScope scope, int limit) {
        String sql = """
                WITH detail_lines AS (
                    %s
                )
                SELECT product_name AS name,
                       'PRODUCT' AS type,
                       COUNT(DISTINCT customer_id) AS customer_count,
                       COUNT(DISTINCT branch_id::text || ':' || order_id::text) AS order_count,
                       COALESCE(SUM(quantity), 0)::numeric AS quantity,
                       COALESCE(SUM(line_total), 0)::numeric AS net_spend
                FROM detail_lines
                WHERE bounced_back = 0
                  AND product_id > 0
                GROUP BY product_name
                ORDER BY quantity DESC, net_spend DESC, product_name ASC
                LIMIT :limit
                """.formatted(detailLinesUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope).addValue("limit", limit(limit)), this::mapPreferenceItem);
    }

    public List<CustomerPreferenceItem> findTopCategories(CustomerBehaviorQueryScope scope, int limit) {
        String sql = """
                WITH detail_lines AS (
                    %s
                )
                SELECT category_name AS name,
                       'CATEGORY' AS type,
                       COUNT(DISTINCT customer_id) AS customer_count,
                       COUNT(DISTINCT branch_id::text || ':' || order_id::text) AS order_count,
                       COALESCE(SUM(quantity), 0)::numeric AS quantity,
                       COALESCE(SUM(line_total), 0)::numeric AS net_spend
                FROM detail_lines
                WHERE bounced_back = 0
                GROUP BY category_name
                ORDER BY net_spend DESC, quantity DESC, category_name ASC
                LIMIT :limit
                """.formatted(detailLinesUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope).addValue("limit", limit(limit)), this::mapPreferenceItem);
    }

    public List<CustomerPreferenceItem> findCustomerTopProducts(CustomerBehaviorQueryScope scope, long customerId, int limit) {
        String sql = """
                WITH detail_lines AS (
                    %s
                )
                SELECT product_name AS name,
                       'PRODUCT' AS type,
                       COUNT(DISTINCT customer_id) AS customer_count,
                       COUNT(DISTINCT branch_id::text || ':' || order_id::text) AS order_count,
                       COALESCE(SUM(quantity), 0)::numeric AS quantity,
                       COALESCE(SUM(line_total), 0)::numeric AS net_spend
                FROM detail_lines
                WHERE bounced_back = 0
                  AND customer_id = :customerId
                  AND product_id > 0
                GROUP BY product_name
                ORDER BY quantity DESC, net_spend DESC, product_name ASC
                LIMIT :limit
                """.formatted(detailLinesUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope)
                .addValue("customerId", customerId)
                .addValue("limit", limit(limit)), this::mapPreferenceItem);
    }

    public List<CustomerPreferenceItem> findCustomerTopCategories(CustomerBehaviorQueryScope scope, long customerId, int limit) {
        String sql = """
                WITH detail_lines AS (
                    %s
                )
                SELECT category_name AS name,
                       'CATEGORY' AS type,
                       COUNT(DISTINCT customer_id) AS customer_count,
                       COUNT(DISTINCT branch_id::text || ':' || order_id::text) AS order_count,
                       COALESCE(SUM(quantity), 0)::numeric AS quantity,
                       COALESCE(SUM(line_total), 0)::numeric AS net_spend
                FROM detail_lines
                WHERE bounced_back = 0
                  AND customer_id = :customerId
                GROUP BY category_name
                ORDER BY net_spend DESC, quantity DESC, category_name ASC
                LIMIT :limit
                """.formatted(detailLinesUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope)
                .addValue("customerId", customerId)
                .addValue("limit", limit(limit)), this::mapPreferenceItem);
    }

    public List<CustomerRecentOrder> findRecentOrders(CustomerBehaviorQueryScope scope, long customerId, int limit) {
        String sql = """
                WITH period_orders AS (
                    %s
                )
                SELECT order_id,
                       branch_id,
                       order_time,
                       order_type,
                       gross_total,
                       discount_total,
                       return_total,
                       net_total
                FROM period_orders
                WHERE customer_id = :customerId
                ORDER BY order_time DESC, order_id DESC
                LIMIT :limit
                """.formatted(periodOrderUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope)
                .addValue("customerId", customerId)
                .addValue("limit", limit(limit)), (rs, rowNum) -> new CustomerRecentOrder(
                rs.getLong("order_id"),
                rs.getInt("branch_id"),
                rs.getTimestamp("order_time").toLocalDateTime(),
                rs.getString("order_type"),
                rs.getBigDecimal("gross_total"),
                rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("return_total"),
                rs.getBigDecimal("net_total")
        ));
    }

    public List<CustomerSpendTrendPoint> findSpendTrend(CustomerBehaviorQueryScope scope, long customerId) {
        String sql = """
                WITH period_orders AS (
                    %s
                )
                SELECT to_char(date_trunc('month', order_time), 'YYYY-MM') AS month,
                       COUNT(*) AS order_count,
                       COALESCE(SUM(net_total), 0)::numeric AS net_spend
                FROM period_orders
                WHERE customer_id = :customerId
                GROUP BY date_trunc('month', order_time)
                ORDER BY date_trunc('month', order_time)
                """.formatted(periodOrderUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope).addValue("customerId", customerId), (rs, rowNum) ->
                new CustomerSpendTrendPoint(
                        rs.getString("month"),
                        rs.getLong("order_count"),
                        rs.getBigDecimal("net_spend")
                ));
    }

    public List<CustomerProductAffinity> findProductAffinity(CustomerBehaviorQueryScope scope, int minimumSupport, int limit) {
        String sql = """
                WITH detail_lines AS (
                    %s
                ),
                order_products AS (
                    SELECT DISTINCT branch_id, order_id, product_id, product_name
                    FROM detail_lines
                    WHERE bounced_back = 0
                      AND product_id > 0
                ),
                product_orders AS (
                    SELECT product_id, COUNT(*) AS order_count
                    FROM order_products
                    GROUP BY product_id
                ),
                pairs AS (
                    SELECT a.product_id AS product_a_id,
                           MIN(a.product_name) AS product_a_name,
                           b.product_id AS product_b_id,
                           MIN(b.product_name) AS product_b_name,
                           COUNT(*) AS support_orders
                    FROM order_products a
                    JOIN order_products b
                      ON b.branch_id = a.branch_id
                     AND b.order_id = a.order_id
                     AND b.product_id > a.product_id
                    GROUP BY a.product_id, b.product_id
                    HAVING COUNT(*) >= :minimumSupport
                )
                SELECT p.product_a_id,
                       p.product_a_name,
                       p.product_b_id,
                       p.product_b_name,
                       p.support_orders,
                       (p.support_orders::numeric / NULLIF(po.order_count, 0))::numeric AS confidence
                FROM pairs p
                JOIN product_orders po ON po.product_id = p.product_a_id
                ORDER BY p.support_orders DESC, confidence DESC
                LIMIT :limit
                """.formatted(detailLinesUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope)
                .addValue("minimumSupport", Math.max(1, minimumSupport))
                .addValue("limit", limit(limit)), (rs, rowNum) -> new CustomerProductAffinity(
                rs.getLong("product_a_id"),
                rs.getString("product_a_name"),
                rs.getLong("product_b_id"),
                rs.getString("product_b_name"),
                rs.getLong("support_orders"),
                rs.getBigDecimal("confidence") == null ? BigDecimal.ZERO : rs.getBigDecimal("confidence")
        ));
    }

    public List<CustomerRetentionCohort> findRetentionCohorts(CustomerBehaviorQueryScope scope) {
        String sql = """
                WITH orders_to_date AS (
                    %s
                ),
                customer_orders AS (
                    SELECT customer_id,
                           MIN(order_time) AS first_order_time,
                           COUNT(*) AS historical_orders,
                           COALESCE(SUM(CASE WHEN order_time >= :fromTime THEN net_total ELSE 0 END), 0)::numeric AS period_spend
                    FROM orders_to_date
                    GROUP BY customer_id
                )
                SELECT to_char(date_trunc('month', first_order_time), 'YYYY-MM') AS cohort_month,
                       COUNT(*) AS customers,
                       COUNT(*) FILTER (WHERE historical_orders >= 2) AS repeat_customers,
                       (COUNT(*) FILTER (WHERE historical_orders >= 2))::numeric / NULLIF(COUNT(*), 0) AS repeat_rate,
                       COALESCE(SUM(period_spend), 0)::numeric AS net_spend
                FROM customer_orders
                WHERE first_order_time >= :fromTime
                  AND first_order_time < :toTime
                GROUP BY date_trunc('month', first_order_time)
                ORDER BY date_trunc('month', first_order_time)
                """.formatted(orderUnionSql(scope));
        return jdbcTemplate.query(sql, params(scope), (rs, rowNum) -> new CustomerRetentionCohort(
                rs.getString("cohort_month"),
                rs.getLong("customers"),
                rs.getLong("repeat_customers"),
                rs.getBigDecimal("repeat_rate") == null ? BigDecimal.ZERO : rs.getBigDecimal("repeat_rate"),
                rs.getBigDecimal("net_spend")
        ));
    }

    private CustomerBehaviorMetricRecord mapMetricRecord(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerBehaviorMetricRecord(
                rs.getLong("c_id"),
                rs.getString("clientName"),
                rs.getString("clientPhone"),
                (Integer) rs.getObject("branchId"),
                rs.getString("branchName"),
                rs.getTimestamp("registeredTime") == null ? null : rs.getTimestamp("registeredTime").toLocalDateTime(),
                rs.getLong("period_order_count"),
                rs.getLong("historical_order_count"),
                rs.getBigDecimal("gross_spend"),
                rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("return_total"),
                rs.getBigDecimal("net_spend"),
                rs.getBigDecimal("purchased_quantity"),
                rs.getTimestamp("first_purchase_at") == null ? null : rs.getTimestamp("first_purchase_at").toLocalDateTime(),
                rs.getTimestamp("last_purchase_at") == null ? null : rs.getTimestamp("last_purchase_at").toLocalDateTime(),
                rs.getString("favorite_product"),
                rs.getString("favorite_category"),
                rs.getBigDecimal("favorite_category_spend"),
                rs.getBigDecimal("total_category_spend")
        );
    }

    private CustomerPreferenceItem mapPreferenceItem(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerPreferenceItem(
                rs.getString("name"),
                rs.getString("type"),
                rs.getLong("customer_count"),
                rs.getLong("order_count"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("net_spend")
        );
    }

    private String periodOrderUnionSql(CustomerBehaviorQueryScope scope) {
        StringJoiner joiner = new StringJoiner("\nUNION ALL\n");
        for (Integer branchId : scope.branchIds()) {
            joiner.add(orderSelectSql(scope.companyId(), branchId, true));
        }
        return joiner.toString();
    }

    private String orderUnionSql(CustomerBehaviorQueryScope scope) {
        StringJoiner joiner = new StringJoiner("\nUNION ALL\n");
        for (Integer branchId : scope.branchIds()) {
            joiner.add(orderSelectSql(scope.companyId(), branchId, false));
        }
        return joiner.toString();
    }

    private String orderSelectSql(long companyId, int branchId, boolean periodOnly) {
        int safeCompanyId = Math.toIntExact(companyId);
        String orderTable = TenantSqlIdentifiers.orderTable(safeCompanyId, branchId);
        String clientTable = TenantSqlIdentifiers.clientTable(safeCompanyId);
        String periodPredicate = periodOnly ? "AND o.\"orderTime\" >= :fromTime " : "";
        return """
                SELECT %s AS branch_id,
                       o."orderId"::bigint AS order_id,
                       o."orderTime" AS order_time,
                       o."clientId"::bigint AS customer_id,
                       COALESCE(NULLIF(TRIM(o."orderType"), ''), 'Unknown') AS order_type,
                       %s AS gross_total,
                       %s AS discount_total,
                       %s AS return_total,
                       %s AS net_total
                FROM %s o
                JOIN %s c ON c.c_id = o."clientId"
                WHERE o."orderTime" < :toTime
                  %s
                  AND %s
                """.formatted(
                Integer.toString(branchId),
                CustomerBehaviorMetricRules.grossOrderExpression("o"),
                CustomerBehaviorMetricRules.discountExpression("o"),
                CustomerBehaviorMetricRules.returnValueExpression("o"),
                CustomerBehaviorMetricRules.netOrderExpression("o"),
                orderTable,
                clientTable,
                periodPredicate,
                CustomerBehaviorMetricRules.validLinkedCustomerPredicate("o", "c")
        );
    }

    private String detailLinesUnionSql(CustomerBehaviorQueryScope scope) {
        StringJoiner joiner = new StringJoiner("\nUNION ALL\n");
        for (Integer branchId : scope.branchIds()) {
            joiner.add(detailLineSelectSql(scope.companyId(), branchId));
        }
        return joiner.toString();
    }

    private String detailLineSelectSql(long companyId, int branchId) {
        int safeCompanyId = Math.toIntExact(companyId);
        String orderTable = TenantSqlIdentifiers.orderTable(safeCompanyId, branchId);
        String detailTable = TenantSqlIdentifiers.orderDetailTable(safeCompanyId, branchId);
        String clientTable = TenantSqlIdentifiers.clientTable(safeCompanyId);
        String inventoryProductTable = TenantSqlIdentifiers.inventoryProductTable(safeCompanyId);
        String legacyProductTable = TenantSqlIdentifiers.productTable(safeCompanyId, branchId);
        return """
                SELECT %s AS branch_id,
                       o."orderId"::bigint AS order_id,
                       o."clientId"::bigint AS customer_id,
                       COALESCE(od."productId", 0)::bigint AS product_id,
                       COALESCE(NULLIF(TRIM(p.product_name), ''), NULLIF(TRIM(lp."productName"), ''), NULLIF(TRIM(od."itemName"), ''), 'Unknown') AS product_name,
                       COALESCE(NULLIF(TRIM(p.major), ''), NULLIF(TRIM(lp."major"), ''), 'Uncategorized') AS category_name,
                       COALESCE(od.quantity, 0)::numeric AS quantity,
                       COALESCE(od.total, 0)::numeric AS line_total,
                       COALESCE(od."bouncedBack", 0) AS bounced_back
                FROM %s o
                JOIN %s od ON od."orderId" = o."orderId"
                JOIN %s c ON c.c_id = o."clientId"
                LEFT JOIN %s p ON p.product_id = od."productId"
                LEFT JOIN %s lp ON lp."productId" = od."productId"
                WHERE o."orderTime" >= :fromTime
                  AND o."orderTime" < :toTime
                  AND %s
                """.formatted(
                Integer.toString(branchId),
                orderTable,
                detailTable,
                clientTable,
                inventoryProductTable,
                legacyProductTable,
                CustomerBehaviorMetricRules.validLinkedCustomerPredicate("o", "c")
        );
    }

    private MapSqlParameterSource params(CustomerBehaviorQueryScope scope) {
        return new MapSqlParameterSource()
                .addValue("branchIds", scope.branchIds())
                .addValue("fromTime", scope.fromTime())
                .addValue("toTime", scope.toTimeExclusive());
    }

    private int limit(int value) {
        return value <= 0 ? DEFAULT_LIMIT : Math.min(value, 100);
    }
}
