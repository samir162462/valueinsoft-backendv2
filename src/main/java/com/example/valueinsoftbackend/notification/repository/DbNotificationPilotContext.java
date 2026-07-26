package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Small, read-only adapter used by the initial business notification integrations.
 * Business services pass durable identifiers; display context is resolved after commit.
 */
@Repository
public class DbNotificationPilotContext {
    private final NamedParameterJdbcTemplate jdbc;

    public DbNotificationPilotContext(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String branchName(int companyId, int branchId) {
        List<String> names = jdbc.query(
                """
                SELECT "branchName"
                FROM public."Branch"
                WHERE "companyId" = :companyId AND "branchId" = :branchId
                """,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId),
                (rs, rowNum) -> rs.getString("branchName"));
        return names.isEmpty() ? "Branch " + branchId : names.get(0);
    }

    public Integer userId(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            return null;
        }
        String normalized = principalName.contains(" : ")
                ? principalName.split(" : ", 2)[0].trim()
                : principalName.trim();
        List<Integer> ids = jdbc.query(
                """
                SELECT id AS "userId"
                FROM public.users
                WHERE "userName" = :userName
                """,
                new MapSqlParameterSource("userName", normalized),
                (rs, rowNum) -> rs.getInt("userId"));
        return ids.isEmpty() ? null : ids.get(0);
    }

    public String companyCurrency(int companyId) {
        List<String> currencies = jdbc.query(
                """
                SELECT currency
                FROM public."Company"
                WHERE id = :companyId
                """,
                new MapSqlParameterSource("companyId", companyId),
                (rs, rowNum) -> rs.getString("currency"));
        return currencies.isEmpty() ? null : currencies.get(0);
    }

    public List<LowStockProduct> lowStockProducts(int companyId,
                                                  int branchId,
                                                  Collection<Long> productIds,
                                                  int threshold) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT p.product_id,
                       p.product_name,
                       GREATEST(COALESCE(st.quantity, 0) - COALESCE(st.reserved_qty, 0), 0) AS available_qty
                FROM %s p
                JOIN %s st
                  ON st.product_id = p.product_id
                 AND st.company_id = :companyId
                 AND st.branch_id = :branchId
                WHERE p.product_id IN (:productIds)
                  AND GREATEST(COALESCE(st.quantity, 0) - COALESCE(st.reserved_qty, 0), 0) <= :threshold
                ORDER BY p.product_id
                """.formatted(
                TenantSqlIdentifiers.inventoryProductTable(companyId),
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));
        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productIds", productIds)
                        .addValue("threshold", Math.max(0, threshold)),
                (rs, rowNum) -> new LowStockProduct(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getInt("available_qty")));
    }

    public record LowStockProduct(long productId, String productName, int availableQuantity) {
    }
}
