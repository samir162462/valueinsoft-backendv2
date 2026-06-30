package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DbInventoryWorkspaceCommandGateway {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbInventoryWorkspaceCommandGateway(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void assignProductToBranch(String actorName, int companyId, int branchId, long productId, Integer defaultSupplierId) {
        String branchProductTable = TenantSqlIdentifiers.inventoryBranchProductTable(companyId);
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);

        // 1. Verify branch belongs to company
        Integer validCompanyId = jdbcTemplate.queryForObject(
                "SELECT \"companyId\" FROM public.\"Branch\" WHERE \"branchId\" = :branchId",
                new MapSqlParameterSource("branchId", branchId),
                Integer.class
        );
        
        if (validCompanyId == null || validCompanyId != companyId) {
            throw new IllegalArgumentException("Branch does not belong to the specified company");
        }

        // 2. Verify product belongs to company tenant schema
        Integer validProductId = jdbcTemplate.queryForObject(
                "SELECT 1 FROM " + productTable + " WHERE product_id = :productId",
                new MapSqlParameterSource("productId", productId),
                Integer.class
        );

        if (validProductId == null) {
            throw new IllegalArgumentException("Product does not belong to the specified company schema");
        }

        // 3. User mapping validation should ideally be done against the user table
        Integer userCompanyId = jdbcTemplate.queryForObject(
                "SELECT company_id FROM public.\"User\" WHERE username = :actorName",
                new MapSqlParameterSource("actorName", actorName),
                Integer.class
        );

        if (userCompanyId == null || userCompanyId != companyId) {
            throw new IllegalArgumentException("User does not have permission for this company");
        }

        String sql = """
                INSERT INTO %s (
                    branch_id, product_id, is_active, default_supplier_id, created_at, updated_at
                ) VALUES (
                    :branchId, :productId, TRUE, :supplierId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                ON CONFLICT (branch_id, product_id) DO UPDATE
                SET is_active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                """.formatted(branchProductTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("productId", productId)
                .addValue("supplierId", defaultSupplierId);

        jdbcTemplate.update(sql, params);
    }
}
