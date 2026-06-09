package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.pricing.dynamic.model.PricingMetricsSnapshot;
import com.example.valueinsoftbackend.pricing.dynamic.model.ProductMovementClass;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class PricingMetricsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PricingMetricsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MetricsPage findMetrics(MetricsQuery query) {
        int page = Math.max(0, query.page());
        int size = Math.min(Math.max(1, query.size()), 100);
        int offset = page * size;

        MapSqlParameterSource params = baseParams(query)
                .addValue("limit", size)
                .addValue("offset", offset);

        String fromClause = buildProductScopeFromClause(query.companyId(), query.branchId());
        String whereClause = buildWhereClause(query, params);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + fromClause + whereClause,
                params,
                Long.class
        );
        long totalItems = total == null ? 0L : total;

        String sql = """
                WITH product_scope AS (
                    SELECT
                        p.product_id,
                        p.product_name,
                        p.major,
                        p.business_line_key,
                        p.template_key,
                        p.pricing_policy_code,
                        p.supplier_id,
                        p.retail_price,
                        p.lowest_price,
                        p.buying_price,
                        COALESCE(stock.quantity, 0)::numeric AS stock_qty,
                        history.buying_price_snapshot AS last_history_buying_price
                    %s
                    %s
                    ORDER BY p.updated_at DESC, p.product_id DESC
                    LIMIT :limit OFFSET :offset
                ),
                sales_agg AS (
                    SELECT
                        detail."productId" AS product_id,
                        COALESCE(SUM(CASE WHEN ord."orderTime" >= :start7 THEN detail.quantity ELSE 0 END), 0)::numeric AS sold_qty_7d,
                        COALESCE(SUM(CASE WHEN ord."orderTime" >= :start30 THEN detail.quantity ELSE 0 END), 0)::numeric AS sold_qty_30d,
                        COALESCE(SUM(CASE WHEN ord."orderTime" >= :start90 THEN detail.quantity ELSE 0 END), 0)::numeric AS sold_qty_90d,
                        COALESCE(SUM(detail.total), 0)::numeric AS net_sales,
                        MAX(ord."orderTime") AS last_sale_at
                    FROM %s detail
                    JOIN %s ord ON ord."orderId" = detail."orderId"
                    WHERE detail."productId" IN (SELECT product_id FROM product_scope)
                      AND ord."orderTime" >= :start90
                      AND ord."orderTime" < :toExclusive
                      AND COALESCE(detail."bouncedBack", 0) = 0
                    GROUP BY detail."productId"
                )
                SELECT
                    scope.product_id,
                    scope.product_name,
                    scope.major,
                    scope.business_line_key,
                    scope.template_key,
                    scope.pricing_policy_code,
                    scope.supplier_id,
                    scope.retail_price::numeric,
                    scope.lowest_price::numeric,
                    scope.buying_price::numeric,
                    scope.stock_qty,
                    COALESCE(sales.sold_qty_7d, 0) AS sold_qty_7d,
                    COALESCE(sales.sold_qty_30d, 0) AS sold_qty_30d,
                    COALESCE(sales.sold_qty_90d, 0) AS sold_qty_90d,
                    sales.last_sale_at,
                    scope.last_history_buying_price
                FROM product_scope scope
                LEFT JOIN sales_agg sales ON sales.product_id = scope.product_id
                ORDER BY scope.product_name ASC, scope.product_id ASC
                """.formatted(
                fromClause,
                whereClause,
                TenantSqlIdentifiers.orderDetailTable(query.companyId(), query.branchId()),
                TenantSqlIdentifiers.orderTable(query.companyId(), query.branchId())
        );

        List<PricingMetricsSnapshot> items = jdbcTemplate.query(sql, params, new MetricsRowMapper(query));
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / (double) size);
        return new MetricsPage(items, page, size, totalItems, totalPages);
    }

    private String buildProductScopeFromClause(int companyId, int branchId) {
        return """
                FROM %s p
                LEFT JOIN %s stock
                  ON stock.product_id = p.product_id
                 AND stock.branch_id = :branchId
                LEFT JOIN LATERAL (
                    SELECT price_history.buying_price_snapshot
                    FROM %s price_history
                    WHERE price_history.product_id = p.product_id
                      AND price_history.company_id = :companyId
                    ORDER BY price_history.created_at DESC
                    LIMIT 1
                ) history ON TRUE
                """.formatted(
                TenantSqlIdentifiers.inventoryProductTable(companyId),
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId),
                TenantSqlIdentifiers.inventoryProductPriceHistoryTable(companyId)
        );
    }

    private String buildWhereClause(MetricsQuery query, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();

        if (query.queryText() != null && !query.queryText().isBlank()) {
            conditions.add("(p.product_name ILIKE :queryText OR p.serial ILIKE :queryText OR p.barcode ILIKE :queryText)");
            params.addValue("queryText", "%" + query.queryText().trim() + "%");
        }
        if (query.productIds() != null && !query.productIds().isEmpty()) {
            conditions.add("p.product_id IN (:productIds)");
            params.addValue("productIds", query.productIds());
        }
        String category = firstText(query.category(), query.major());
        if (category != null) {
            conditions.add("LOWER(p.major) = :category");
            params.addValue("category", category.toLowerCase(Locale.ROOT));
        }
        if (query.businessLineKey() != null && !query.businessLineKey().isBlank()) {
            conditions.add("LOWER(p.business_line_key) = :businessLineKey");
            params.addValue("businessLineKey", query.businessLineKey().trim().toLowerCase(Locale.ROOT));
        }
        if (query.templateKey() != null && !query.templateKey().isBlank()) {
            conditions.add("LOWER(p.template_key) = :templateKey");
            params.addValue("templateKey", query.templateKey().trim().toLowerCase(Locale.ROOT));
        }
        if (query.supplierId() != null) {
            conditions.add("p.supplier_id = :supplierId");
            params.addValue("supplierId", query.supplierId());
        }

        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private MapSqlParameterSource baseParams(MetricsQuery query) {
        LocalDate toDate = query.toDate();
        LocalDate start7 = maxDate(query.fromDate(), toDate.minusDays(6));
        LocalDate start30 = maxDate(query.fromDate(), toDate.minusDays(29));
        LocalDate start90 = maxDate(query.fromDate(), toDate.minusDays(89));
        return new MapSqlParameterSource()
                .addValue("companyId", query.companyId())
                .addValue("branchId", query.branchId())
                .addValue("start7", Timestamp.valueOf(start7.atStartOfDay()))
                .addValue("start30", Timestamp.valueOf(start30.atStartOfDay()))
                .addValue("start90", Timestamp.valueOf(start90.atStartOfDay()))
                .addValue("toExclusive", Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
    }

    private LocalDate maxDate(LocalDate left, LocalDate right) {
        return left.isAfter(right) ? left : right;
    }

    private static String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static final class MetricsRowMapper implements RowMapper<PricingMetricsSnapshot> {
        private static final BigDecimal VELOCITY_FLOOR = new BigDecimal("0.0500");
        private final MetricsQuery query;
        private final BigDecimal days7;
        private final BigDecimal days30;
        private final BigDecimal days90;

        private MetricsRowMapper(MetricsQuery query) {
            this.query = query;
            this.days7 = BigDecimal.valueOf(Math.min(7, Math.max(1, ChronoUnit.DAYS.between(query.fromDate(), query.toDate().plusDays(1)))));
            this.days30 = BigDecimal.valueOf(Math.min(30, Math.max(1, ChronoUnit.DAYS.between(query.fromDate(), query.toDate().plusDays(1)))));
            this.days90 = BigDecimal.valueOf(Math.min(90, Math.max(1, ChronoUnit.DAYS.between(query.fromDate(), query.toDate().plusDays(1)))));
        }

        @Override
        public PricingMetricsSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            BigDecimal retailPrice = nonNull(rs.getBigDecimal("retail_price"));
            BigDecimal buyingPrice = nonNull(rs.getBigDecimal("buying_price"));
            BigDecimal stockQty = nonNull(rs.getBigDecimal("stock_qty"));
            BigDecimal sold7 = nonNull(rs.getBigDecimal("sold_qty_7d"));
            BigDecimal sold30 = nonNull(rs.getBigDecimal("sold_qty_30d"));
            BigDecimal sold90 = nonNull(rs.getBigDecimal("sold_qty_90d"));
            BigDecimal velocity7 = divide(sold7, days7);
            BigDecimal velocity30 = divide(sold30, days30);
            BigDecimal velocity90 = divide(sold90, days90);
            BigDecimal weightedVelocity = velocity7.multiply(new BigDecimal("0.50"))
                    .add(velocity30.multiply(new BigDecimal("0.35")))
                    .add(velocity90.multiply(new BigDecimal("0.15")))
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal daysCover = divide(stockQty, weightedVelocity.max(VELOCITY_FLOOR));
            BigDecimal currentMargin = retailPrice.compareTo(BigDecimal.ZERO) > 0
                    ? divide(retailPrice.subtract(buyingPrice), retailPrice)
                    : null;
            BigDecimal costChangePct = costChangePct(buyingPrice, rs.getBigDecimal("last_history_buying_price"));
            ProductMovementClass movementClass = classifyMovement(stockQty, velocity30, daysCover, rs.getTimestamp("last_sale_at"));
            BigDecimal demandScore = demandScore(velocity7, velocity30, daysCover);

            return new PricingMetricsSnapshot(
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getString("major"),
                    rs.getString("business_line_key"),
                    rs.getString("template_key"),
                    rs.getString("pricing_policy_code"),
                    getIntegerOrNull(rs, "supplier_id"),
                    retailPrice,
                    nonNull(rs.getBigDecimal("lowest_price")),
                    buyingPrice,
                    stockQty,
                    sold7,
                    sold30,
                    sold90,
                    velocity7,
                    velocity30,
                    velocity90,
                    weightedVelocity,
                    daysCover,
                    currentMargin,
                    movementClass,
                    demandScore,
                    costChangePct,
                    toOffsetDateTime(rs.getTimestamp("last_sale_at"))
            );
        }

        private ProductMovementClass classifyMovement(BigDecimal stockQty, BigDecimal velocity30, BigDecimal daysCover, Timestamp lastSaleAt) {
            if (stockQty.compareTo(BigDecimal.ZERO) <= 0 && velocity30.compareTo(BigDecimal.ZERO) <= 0) {
                return ProductMovementClass.UNKNOWN;
            }
            if (velocity30.compareTo(new BigDecimal("1.0000")) >= 0 || daysCover.compareTo(new BigDecimal("7.0000")) <= 0) {
                return ProductMovementClass.FAST;
            }
            if (lastSaleAt == null && stockQty.compareTo(BigDecimal.ZERO) > 0) {
                return ProductMovementClass.DEAD;
            }
            if (velocity30.compareTo(new BigDecimal("0.1000")) < 0 && stockQty.compareTo(BigDecimal.ZERO) > 0) {
                return ProductMovementClass.SLOW;
            }
            return ProductMovementClass.STABLE;
        }

        private BigDecimal demandScore(BigDecimal velocity7, BigDecimal velocity30, BigDecimal daysCover) {
            BigDecimal velocityComponent = velocity30.min(new BigDecimal("2.0000")).divide(new BigDecimal("2.0000"), 4, RoundingMode.HALF_UP);
            BigDecimal growthComponent = velocity30.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : velocity7.divide(velocity30, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            BigDecimal stockPressure = BigDecimal.ONE.subtract(daysCover.min(new BigDecimal("60.0000")).divide(new BigDecimal("60.0000"), 4, RoundingMode.HALF_UP));
            return velocityComponent.multiply(new BigDecimal("0.55"))
                    .add(growthComponent.multiply(new BigDecimal("0.25")))
                    .add(stockPressure.multiply(new BigDecimal("0.20")))
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.ONE)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        private static BigDecimal costChangePct(BigDecimal currentBuyingPrice, BigDecimal previousBuyingPrice) {
            if (previousBuyingPrice == null || previousBuyingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return divide(currentBuyingPrice.subtract(previousBuyingPrice), previousBuyingPrice);
        }

        private static BigDecimal nonNull(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
            if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
        }

        private static Integer getIntegerOrNull(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : value;
        }

        private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
    }

    public record MetricsQuery(
            int companyId,
            int branchId,
            LocalDate fromDate,
            LocalDate toDate,
            String queryText,
            List<Long> productIds,
            String category,
            String major,
            String businessLineKey,
            String templateKey,
            Integer supplierId,
            int page,
            int size
    ) {
    }

    public record MetricsPage(
            List<PricingMetricsSnapshot> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {
    }
}
