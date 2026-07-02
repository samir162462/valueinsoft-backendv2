package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.Response.UserPerformanceResponse;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class DbUserPerformance {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbUserPerformance(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /**
     * Aggregate sales totals for a single salesperson within a date range (exclusive end).
     */
    public UserPerformanceResponse.PerformanceTotals getTotals(int companyId, int branchId, String salesUser,
                                                                LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);

        String sql = """
                SELECT
                    COALESCE(SUM(o."orderTotal"), 0)::double precision AS sales_total,
                    COUNT(o."orderId") AS orders_count,
                    COALESCE(SUM(o."orderIncome"), 0)::double precision AS income_total,
                    COUNT(DISTINCT o."clientId") AS distinct_clients
                FROM %s o
                WHERE o."salesUser" = :salesUser
                  AND o."orderTime" >= :fromTs
                  AND o."orderTime" < :toTs
                """.formatted(orderTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("salesUser", salesUser)
                .addValue("fromTs", Timestamp.valueOf(fromInclusive))
                .addValue("toTs", Timestamp.valueOf(toExclusive));

        UserPerformanceResponse.PerformanceTotals totals = namedParameterJdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
            UserPerformanceResponse.PerformanceTotals row = new UserPerformanceResponse.PerformanceTotals();
            double salesTotal = rs.getDouble("sales_total");
            int ordersCount = rs.getInt("orders_count");
            row.setSalesTotal(salesTotal);
            row.setOrdersCount(ordersCount);
            row.setIncomeTotal(rs.getDouble("income_total"));
            row.setDistinctClients(rs.getInt("distinct_clients"));
            row.setAvgOrderValue(ordersCount > 0 ? salesTotal / ordersCount : 0.0);
            return row;
        });

        return totals != null ? totals : new UserPerformanceResponse.PerformanceTotals();
    }

    /**
     * Gap-filled bucketed sales series for a salesperson within a date range (exclusive end).
     * bucketUnit must be one of: hour, day, week, month.
     */
    public List<UserPerformanceResponse.PerformancePoint> getSeries(int companyId, int branchId, String salesUser,
                                                                     LocalDateTime fromInclusive, LocalDateTime toExclusive,
                                                                     String bucketUnit) {
        String orderTable = TenantSqlIdentifiers.orderTable(companyId, branchId);
        String truncUnit = normalizeBucketUnit(bucketUnit);
        String stepInterval = switch (truncUnit) {
            case "hour" -> "1 hour";
            case "week" -> "1 week";
            case "month" -> "1 month";
            default -> "1 day";
        };

        String sql = """
                SELECT
                    gs AS bucket_start,
                    COALESCE(SUM(o."orderTotal"), 0)::double precision AS bucket_total,
                    COUNT(o."orderId") AS bucket_orders
                FROM generate_series(
                         date_trunc('%1$s', :fromTs::timestamp),
                         date_trunc('%1$s', (:toTs::timestamp - interval '1 second')),
                         interval '%2$s'
                     ) gs
                LEFT JOIN %3$s o
                    ON date_trunc('%1$s', o."orderTime") = gs
                    AND o."salesUser" = :salesUser
                    AND o."orderTime" >= :fromTs
                    AND o."orderTime" < :toTs
                GROUP BY gs
                ORDER BY gs
                """.formatted(truncUnit, stepInterval, orderTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("salesUser", salesUser)
                .addValue("fromTs", Timestamp.valueOf(fromInclusive))
                .addValue("toTs", Timestamp.valueOf(toExclusive));

        DateTimeFormatter labelFormatter = labelFormatterFor(truncUnit);

        return new ArrayList<>(namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Timestamp bucketStart = rs.getTimestamp("bucket_start");
            LocalDateTime bucketDateTime = bucketStart == null ? null : bucketStart.toLocalDateTime();
            String label = bucketDateTime == null ? "" : labelFormatter.format(bucketDateTime);
            return new UserPerformanceResponse.PerformancePoint(
                    label,
                    rs.getDouble("bucket_total"),
                    rs.getInt("bucket_orders")
            );
        }));
    }

    private String normalizeBucketUnit(String bucketUnit) {
        if (bucketUnit == null) {
            return "day";
        }
        return switch (bucketUnit) {
            case "hour", "week", "month" -> bucketUnit;
            default -> "day";
        };
    }

    private DateTimeFormatter labelFormatterFor(String truncUnit) {
        return switch (truncUnit) {
            case "hour" -> DateTimeFormatter.ofPattern("ha", Locale.ENGLISH);
            case "week" -> DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
            case "month" -> DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
            default -> DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        };
    }
}
