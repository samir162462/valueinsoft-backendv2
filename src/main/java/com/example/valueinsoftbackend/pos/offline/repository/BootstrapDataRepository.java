package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.dto.response.BootstrapPage;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapPriceItem;
import com.example.valueinsoftbackend.pos.offline.dto.response.OfflineBootstrapProductItem;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class BootstrapDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public BootstrapDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BootstrapPage<OfflineBootstrapProductItem> findProducts(Long companyId, Long branchId,
                                                                   Long afterProductId, int pageSize) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String sql = """
                SELECT
                    p.product_id,
                    p.serial AS barcode,
                    p.product_name,
                    p.retail_price,
                    p.lowest_price,
                    COALESCE(s.quantity, 0) AS current_stock,
                    p.major AS category,
                    p.product_state,
                    p.base_uom_code,
                    p.pricing_policy_code,
                    p.updated_at
                FROM %s p
                JOIN %s s
                  ON s.product_id = p.product_id
                 AND s.branch_id = ?
                WHERE p.product_id > ?
                ORDER BY p.product_id ASC
                LIMIT ?
                """.formatted(productTable, stockTable);

        List<OfflineBootstrapProductItem> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapProductItem(
                rs.getLong("product_id"),
                rs.getString("barcode"),
                rs.getString("product_name"),
                rs.getBigDecimal("retail_price"),
                rs.getBigDecimal("lowest_price"),
                rs.getInt("current_stock"),
                rs.getString("category"),
                isActiveProductState(rs.getString("product_state")),
                rs.getString("base_uom_code"),
                rs.getString("pricing_policy_code"),
                toInstant(rs.getTimestamp("updated_at"))
        ), branchId, afterProductId, pageSize + 1);

        return toPage(rows, pageSize, OfflineBootstrapProductItem::productId, this::productUpdatedAt);
    }

    public BootstrapPage<OfflineBootstrapPriceItem> findPrices(Long companyId, Long branchId,
                                                               Long afterProductId, int pageSize) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String pricingPolicyTable = TenantSqlIdentifiers.inventoryPricingPolicyTable(companyId);
        String sql = """
                SELECT
                    p.product_id,
                    p.retail_price,
                    p.lowest_price,
                    p.buying_price,
                    p.pricing_policy_code,
                    policy.strategy_type,
                    policy.config_json::text AS config_json,
                    p.updated_at
                FROM %s p
                JOIN %s s
                  ON s.product_id = p.product_id
                 AND s.branch_id = ?
                LEFT JOIN %s policy
                  ON policy.pricing_policy_code = p.pricing_policy_code
                WHERE p.product_id > ?
                ORDER BY p.product_id ASC
                LIMIT ?
                """.formatted(productTable, stockTable, pricingPolicyTable);

        List<OfflineBootstrapPriceItem> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineBootstrapPriceItem(
                rs.getLong("product_id"),
                rs.getBigDecimal("retail_price"),
                rs.getBigDecimal("lowest_price"),
                rs.getBigDecimal("buying_price"),
                rs.getString("pricing_policy_code"),
                rs.getString("strategy_type"),
                rs.getString("config_json"),
                toInstant(rs.getTimestamp("updated_at"))
        ), branchId, afterProductId, pageSize + 1);

        return toPage(rows, pageSize, OfflineBootstrapPriceItem::productId, OfflineBootstrapPriceItem::updatedAt);
    }

    private <T> BootstrapPage<T> toPage(List<T> fetched, int pageSize, IdExtractor<T> idExtractor,
                                        UpdatedAtExtractor<T> updatedAtExtractor) {
        boolean hasMore = fetched.size() > pageSize;
        List<T> items = hasMore ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = String.valueOf(idExtractor.extract(items.get(items.size() - 1)));
        }

        Instant lastUpdatedAt = null;
        for (T item : items) {
            Instant updatedAt = updatedAtExtractor.extract(item);
            if (updatedAt != null && (lastUpdatedAt == null || updatedAt.isAfter(lastUpdatedAt))) {
                lastUpdatedAt = updatedAt;
            }
        }

        return new BootstrapPage<>(items, hasMore, nextCursor, lastUpdatedAt);
    }

    private Boolean isActiveProductState(String productState) {
        return productState == null || !"INACTIVE".equalsIgnoreCase(productState);
    }

    private Instant productUpdatedAt(OfflineBootstrapProductItem item) {
        return item.updatedAt();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private interface IdExtractor<T> {
        Long extract(T item);
    }

    private interface UpdatedAtExtractor<T> {
        Instant extract(T item);
    }
}
