package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class InflationPricingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InflationPricingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ProductRow(
            long productId,
            String productName,
            BigDecimal buyingPrice,
            BigDecimal retailPrice,
            BigDecimal lowestPrice
    ) {}

    private static final RowMapper<ProductRow> PRODUCT_MAPPER = (rs, rowNum) -> new ProductRow(
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getBigDecimal("buying_price"),
            rs.getBigDecimal("retail_price"),
            rs.getBigDecimal("lowest_price")
    );

    public List<ProductRow> findProductsForInflation(int companyId, int branchId, String scopeType, String scopeValue, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("limit", limit);

        List<String> conditions = new ArrayList<>();
        conditions.add("company_id = :companyId");

        if (scopeType != null && scopeValue != null && !scopeValue.isBlank()) {
            switch (scopeType.toUpperCase(Locale.ROOT)) {
                case "CATEGORY":
                    conditions.add("LOWER(major) = :scopeValue");
                    params.addValue("scopeValue", scopeValue.trim().toLowerCase(Locale.ROOT));
                    break;
                case "SUPPLIER":
                    conditions.add("supplier_id = :supplierId");
                    params.addValue("supplierId", Integer.parseInt(scopeValue.trim()));
                    break;
                case "BUSINESS_LINE":
                    conditions.add("LOWER(business_line_key) = :scopeValue");
                    params.addValue("scopeValue", scopeValue.trim().toLowerCase(Locale.ROOT));
                    break;
                case "TEMPLATE":
                    conditions.add("LOWER(template_key) = :scopeValue");
                    params.addValue("scopeValue", scopeValue.trim().toLowerCase(Locale.ROOT));
                    break;
                default:
                    break;
            }
        }

        String sql = """
                SELECT product_id, product_name, buying_price, retail_price, lowest_price
                FROM %s
                WHERE %s
                ORDER BY product_name ASC, product_id ASC
                LIMIT :limit
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId), String.join(" AND ", conditions));

        return jdbcTemplate.query(sql, params, PRODUCT_MAPPER);
    }

    public int applyBulkInflation(int companyId, int branchId, String scopeType, String scopeValue,
                                  BigDecimal rate, boolean adjustBuying, boolean adjustRetail, boolean adjustLowest,
                                  String mode, BigDecimal roundingFactor, List<Long> productIds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("rate", rate)
                .addValue("adjustBuying", adjustBuying)
                .addValue("adjustRetail", adjustRetail)
                .addValue("adjustLowest", adjustLowest)
                .addValue("isPercentage", "PERCENTAGE".equalsIgnoreCase(mode));

        List<String> conditions = new ArrayList<>();
        conditions.add("company_id = :companyId");

        if (productIds != null && !productIds.isEmpty()) {
            conditions.add("product_id IN (:productIds)");
            params.addValue("productIds", productIds);
        } else if (scopeType != null && scopeValue != null && !scopeValue.isBlank()) {
            switch (scopeType.toUpperCase(Locale.ROOT)) {
                case "CATEGORY":
                    conditions.add("LOWER(major) = :scopeValue");
                    params.addValue("scopeValue", scopeValue.trim().toLowerCase(Locale.ROOT));
                    break;
                case "SUPPLIER":
                    conditions.add("supplier_id = :supplierId");
                    params.addValue("supplierId", Integer.parseInt(scopeValue.trim()));
                    break;
                case "BUSINESS_LINE":
                    conditions.add("LOWER(business_line_key) = :scopeValue");
                    params.addValue("scopeValue", scopeValue.trim().toLowerCase(Locale.ROOT));
                    break;
                case "TEMPLATE":
                    conditions.add("LOWER(template_key) = :scopeValue");
                    params.addValue("scopeValue", scopeValue.trim().toLowerCase(Locale.ROOT));
                    break;
                default:
                    break;
            }
        }

        // Compute raw adjusted values (rate is pre-signed: negative for DECREASE)
        // PERCENTAGE: new = current * (1 + rate)   e.g. rate=0.05 → +5%, rate=-0.05 → -5%
        // FIXED:      new = current + rate           e.g. rate=10 → +10, rate=-10 → -10
        String buyingRaw  = "CASE WHEN :isPercentage THEN GREATEST(0, buying_price  * (1 + :rate)) ELSE GREATEST(0, buying_price  + :rate) END";
        String retailRaw  = "CASE WHEN :isPercentage THEN GREATEST(0, retail_price  * (1 + :rate)) ELSE GREATEST(0, retail_price  + :rate) END";
        String lowestRaw  = "CASE WHEN :isPercentage THEN GREATEST(0, lowest_price  * (1 + :rate)) ELSE GREATEST(0, lowest_price  + :rate) END";

        // Apply optional rounding (only to retail and lowest; buying stays unrounded)
        String retailExpr = retailRaw;
        String lowestExpr = lowestRaw;
        if (roundingFactor != null && roundingFactor.compareTo(BigDecimal.ZERO) > 0) {
            params.addValue("roundingFactor", roundingFactor);
            retailExpr = "ROUND((" + retailRaw + ") / :roundingFactor) * :roundingFactor";
            lowestExpr = "ROUND((" + lowestRaw + ") / :roundingFactor) * :roundingFactor";
        }

        // Compute effective values (what each column will become, whether adjusted or not)
        // These are used to enforce price-order clamping across all three columns.
        String effectiveBuying = "CASE WHEN :adjustBuying THEN (" + buyingRaw  + ") ELSE buying_price END";
        String effectiveLowest = "CASE WHEN :adjustLowest THEN (" + lowestExpr + ") ELSE lowest_price END";
        String effectiveRetail = "CASE WHEN :adjustRetail THEN (" + retailExpr + ") ELSE retail_price END";

        // Final clamped values:
        //   final_buying  = effective_buying  (floor: 0)
        //   final_lowest  = GREATEST(effective_lowest, final_buying)   → lowest ≥ buying
        //   final_retail  = GREATEST(effective_retail, final_lowest)   → retail ≥ lowest
        String finalBuying = "GREATEST(0, " + effectiveBuying + ")";
        String finalLowest = "GREATEST(GREATEST(0, " + effectiveBuying + "), " + effectiveLowest + ")";
        String finalRetail = "GREATEST(GREATEST(GREATEST(0, " + effectiveBuying + "), " + effectiveLowest + "), " + effectiveRetail + ")";

        String sql = """
                UPDATE %s
                SET
                    buying_price = (%s)::numeric(19,4),
                    lowest_price = (%s)::numeric(19,4),
                    retail_price = (%s)::numeric(19,4),
                    updated_at = CURRENT_TIMESTAMP
                WHERE %s
                """.formatted(
                        TenantSqlIdentifiers.inventoryProductTable(companyId),
                        finalBuying,
                        finalLowest,
                        finalRetail,
                        String.join(" AND ", conditions)
                );

        return jdbcTemplate.update(sql, params);
    }
}

