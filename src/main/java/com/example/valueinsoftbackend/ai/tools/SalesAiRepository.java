package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

@Repository
public class SalesAiRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesAiRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SalesAiSummaryDto getSalesSummary(long companyId, long branchId, LocalDate fromDate, LocalDate toDate) {
        String sql = """
                SELECT
                    COUNT(*) AS order_count,
                    COALESCE(SUM("orderTotal"), 0) AS gross_sales,
                    COALESCE(SUM("orderDiscount"), 0) AS discount_total,
                    COALESCE(SUM("orderTotal" - COALESCE("orderDiscount", 0) - COALESCE("orderBouncedBack", 0)), 0) AS net_sales,
                    COALESCE(SUM("orderIncome"), 0) AS income_total,
                    COALESCE(SUM("orderBouncedBack"), 0) AS refund_total
                FROM %s
                WHERE "orderTime" >= :fromTime
                  AND "orderTime" < :toTime
                """.formatted(TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)));
        return jdbcTemplate.queryForObject(sql, dateParams(fromDate, toDate), (rs, rowNum) -> new SalesAiSummaryDto(
                branchId,
                fromDate,
                toDate,
                rs.getLong("order_count"),
                rs.getBigDecimal("gross_sales"),
                rs.getBigDecimal("discount_total"),
                rs.getBigDecimal("net_sales"),
                rs.getBigDecimal("income_total"),
                rs.getBigDecimal("refund_total")
        ));
    }

    public List<SalesAiTopProductDto> getTopSellingProducts(long companyId,
                                                            long branchId,
                                                            LocalDate fromDate,
                                                            LocalDate toDate,
                                                            int limit) {
        String sql = """
                SELECT
                    od."productId" AS product_id,
                    od."itemName" AS product_name,
                    COALESCE(SUM(od.quantity), 0) AS quantity_sold,
                    COALESCE(SUM(od.total), 0) AS sales_total
                FROM %s ord
                JOIN %s od ON od."orderId" = ord."orderId"
                WHERE ord."orderTime" >= :fromTime
                  AND ord."orderTime" < :toTime
                  AND COALESCE(od."bouncedBack", 0) = 0
                GROUP BY od."productId", od."itemName"
                ORDER BY quantity_sold DESC, sales_total DESC
                LIMIT :limit
                """.formatted(
                TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)),
                TenantSqlIdentifiers.orderDetailTable(Math.toIntExact(companyId), Math.toIntExact(branchId))
        );
        return jdbcTemplate.query(sql, dateParams(fromDate, toDate).addValue("limit", limit), (rs, rowNum) ->
                new SalesAiTopProductDto(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getLong("quantity_sold"),
                        rs.getBigDecimal("sales_total")
                ));
    }

    public List<SalesAiCashierDto> getSalesByCashier(long companyId,
                                                     long branchId,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {
        String sql = """
                SELECT
                    COALESCE(NULLIF(TRIM("salesUser"), ''), 'Unknown') AS cashier_name,
                    COUNT(*) AS order_count,
                    COALESCE(SUM("orderTotal"), 0) AS sales_total,
                    COALESCE(SUM("orderIncome"), 0) AS income_total
                FROM %s
                WHERE "orderTime" >= :fromTime
                  AND "orderTime" < :toTime
                GROUP BY COALESCE(NULLIF(TRIM("salesUser"), ''), 'Unknown')
                ORDER BY sales_total DESC, order_count DESC
                LIMIT 25
                """.formatted(TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)));
        return jdbcTemplate.query(sql, dateParams(fromDate, toDate), (rs, rowNum) -> new SalesAiCashierDto(
                rs.getString("cashier_name"),
                rs.getLong("order_count"),
                rs.getBigDecimal("sales_total"),
                rs.getBigDecimal("income_total")
        ));
    }

    public List<PaymentBreakdownDto> getPaymentBreakdown(long companyId,
                                                         long branchId,
                                                         LocalDate fromDate,
                                                         LocalDate toDate) {
        String sql = """
                SELECT
                    COALESCE(NULLIF(TRIM("orderType"), ''), 'Unknown') AS payment_type,
                    COUNT(*) AS transaction_count,
                    COALESCE(SUM("orderTotal"), 0) AS total_amount
                FROM %s
                WHERE "orderTime" >= :fromTime
                  AND "orderTime" < :toTime
                GROUP BY COALESCE(NULLIF(TRIM("orderType"), ''), 'Unknown')
                ORDER BY total_amount DESC
                """.formatted(TenantSqlIdentifiers.orderTable(Math.toIntExact(companyId), Math.toIntExact(branchId)));
        return jdbcTemplate.query(sql, dateParams(fromDate, toDate), (rs, rowNum) -> new PaymentBreakdownDto(
                rs.getString("payment_type"),
                rs.getLong("transaction_count"),
                rs.getBigDecimal("total_amount")
        ));
    }

    private MapSqlParameterSource dateParams(LocalDate fromDate, LocalDate toDate) {
        return new MapSqlParameterSource()
                .addValue("fromTime", Timestamp.valueOf(fromDate.atStartOfDay()))
                .addValue("toTime", Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
    }
}
