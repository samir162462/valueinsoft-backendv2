package com.example.valueinsoftbackend.ai.tools;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InventoryAiRepository {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private static final RowMapper<InventoryAiProductDto> PRODUCT_ROW_MAPPER = (rs, rowNum) -> {
        int quantity = rs.getInt("quantity_on_hand");
        int reserved = rs.getInt("reserved_quantity");
        int available = Math.max(0, quantity - reserved);
        return new InventoryAiProductDto(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("barcode"),
                rs.getString("category"),
                rs.getString("product_type"),
                quantity,
                reserved,
                available,
                stockStatus(quantity),
                rs.getInt("retail_price")
        );
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryAiRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InventoryAiProductDto> getLowStockProducts(long companyId, long branchId, int limit) {
        String sql = baseSelect(companyId) + """
                WHERE COALESCE(st.quantity, 0) > 0
                  AND COALESCE(st.quantity, 0) <= :threshold
                ORDER BY COALESCE(st.quantity, 0) ASC, p.product_name ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(
                sql,
                baseParams(branchId)
                        .addValue("threshold", LOW_STOCK_THRESHOLD)
                        .addValue("limit", limit),
                PRODUCT_ROW_MAPPER
        );
    }

    public List<InventoryAiProductDto> searchProductByName(long companyId, long branchId, String productName, int limit) {
        String sql = baseSelect(companyId) + """
                WHERE p.product_name ILIKE :productName
                ORDER BY p.product_name ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(
                sql,
                baseParams(branchId)
                        .addValue("productName", "%" + nullSafe(productName) + "%")
                        .addValue("limit", limit),
                PRODUCT_ROW_MAPPER
        );
    }

    public Optional<InventoryAiProductDto> getProductByBarcode(long companyId, long branchId, String barcode) {
        String sql = baseSelect(companyId) + """
                WHERE p.serial = :barcode
                ORDER BY p.product_id ASC
                LIMIT 1
                """;
        List<InventoryAiProductDto> rows = jdbcTemplate.query(
                sql,
                baseParams(branchId).addValue("barcode", nullSafe(barcode)),
                PRODUCT_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public Optional<InventoryAiProductDto> getProductStock(long companyId, long branchId, long productId) {
        String sql = baseSelect(companyId) + """
                WHERE p.product_id = :productId
                ORDER BY p.product_id ASC
                LIMIT 1
                """;
        List<InventoryAiProductDto> rows = jdbcTemplate.query(
                sql,
                baseParams(branchId).addValue("productId", productId),
                PRODUCT_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public long countProductsInStock(long companyId, long branchId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String sql = """
                SELECT COUNT(DISTINCT p.product_id)
                FROM %s p
                INNER JOIN %s st ON st.product_id = p.product_id
                WHERE st.branch_id = :branchId
                  AND COALESCE(st.quantity, 0) > 0
                """.formatted(productTable, stockTable);
        Long count = jdbcTemplate.queryForObject(sql, baseParams(branchId), Long.class);
        return count == null ? 0L : count;
    }

    private String baseSelect(long companyId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        return """
                SELECT
                    p.product_id,
                    p.product_name,
                    p.serial AS barcode,
                    p.major AS category,
                    p.product_type,
                    COALESCE(st.quantity, 0) AS quantity_on_hand,
                    COALESCE(st.reserved_qty, 0) AS reserved_quantity,
                    p.retail_price
                FROM %s p
                LEFT JOIN %s st ON st.product_id = p.product_id AND st.branch_id = :branchId
                """.formatted(productTable, stockTable);
    }

    private MapSqlParameterSource baseParams(long branchId) {
        return new MapSqlParameterSource().addValue("branchId", Math.toIntExact(branchId));
    }

    private static String stockStatus(int quantity) {
        if (quantity <= 0) {
            return "OUT_OF_STOCK";
        }
        if (quantity <= LOW_STOCK_THRESHOLD) {
            return "LOW_STOCK";
        }
        return "IN_STOCK";
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}
