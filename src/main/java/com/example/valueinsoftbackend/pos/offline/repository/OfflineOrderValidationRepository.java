package com.example.valueinsoftbackend.pos.offline.repository;

import com.example.valueinsoftbackend.pos.offline.model.OfflineValidationProductSnapshot;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
public class OfflineOrderValidationRepository {

    private final JdbcTemplate jdbcTemplate;

    public OfflineOrderValidationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean branchBelongsToCompany(Long companyId, Long branchId) {
        String sql = """
                SELECT COUNT(*)
                FROM public."Branch"
                WHERE "branchId" = ? AND "companyId" = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, branchId, companyId);
        return count != null && count > 0;
    }

    public boolean cashierBelongsToBranch(Long cashierId, Long branchId) {
        String sql = """
                SELECT COUNT(*)
                FROM public.users
                WHERE id = ? AND "branchId" = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cashierId, branchId);
        return count != null && count > 0;
    }

    public List<OfflineValidationProductSnapshot> findProductsForValidation(Long companyId, Long branchId,
                                                                            Collection<Long> productIds,
                                                                            Collection<String> barcodes) {
        if (productIds.isEmpty() && barcodes.isEmpty()) {
            return List.of();
        }

        List<Object> params = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        if (!productIds.isEmpty()) {
            predicates.add("p.product_id IN (" + placeholders(productIds.size()) + ")");
            params.addAll(productIds);
        }
        if (!barcodes.isEmpty()) {
            predicates.add("p.serial IN (" + placeholders(barcodes.size()) + ")");
            params.addAll(barcodes);
        }

        String sql = """
                SELECT
                    p.product_id,
                    p.serial AS barcode,
                    p.retail_price,
                    p.lowest_price,
                    p.product_state,
                    CASE WHEN s.product_id IS NULL THEN FALSE ELSE TRUE END AS available_in_branch
                FROM %s p
                LEFT JOIN %s s
                  ON s.product_id = p.product_id
                 AND s.branch_id = ?
                WHERE %s
                """.formatted(
                TenantSqlIdentifiers.inventoryProductTable(companyId),
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId),
                String.join(" OR ", predicates));

        params.add(0, branchId);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new OfflineValidationProductSnapshot(
                rs.getLong("product_id"),
                rs.getString("barcode"),
                rs.getBigDecimal("retail_price"),
                rs.getBigDecimal("lowest_price"),
                rs.getBoolean("available_in_branch"),
                isActiveProductState(rs.getString("product_state"))
        ), params.toArray());
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private boolean isActiveProductState(String productState) {
        return productState == null || !"INACTIVE".equalsIgnoreCase(productState);
    }
}
