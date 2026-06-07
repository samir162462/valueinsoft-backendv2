package com.example.valueinsoftbackend.pricing.dynamic.repository;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PriceHistoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PriceHistoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAppliedPriceChange(int companyId, int branchId, long batchId,
                                         PriceAdjustmentBatchRepository.ApplyItemRow item,
                                         String actorName, String reason) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, product_id,
                    old_retail_price, new_retail_price, old_lowest_price, new_lowest_price,
                    buying_price_snapshot, change_source, batch_id, recommendation_run_id,
                    reason, actor_name, created_at
                ) VALUES (
                    :companyId, :branchId, :productId,
                    :oldRetailPrice, :newRetailPrice, :oldLowestPrice, :newLowestPrice,
                    :buyingPriceSnapshot, 'BULK_ADJUSTMENT', :batchId, NULL,
                    :reason, :actorName, NOW()
                )
                """.formatted(TenantSqlIdentifiers.inventoryProductPriceHistoryTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", item.productId())
                .addValue("oldRetailPrice", item.oldRetailPrice())
                .addValue("newRetailPrice", item.newRetailPrice())
                .addValue("oldLowestPrice", item.oldLowestPrice())
                .addValue("newLowestPrice", item.newLowestPrice())
                .addValue("buyingPriceSnapshot", item.buyingPriceSnapshot())
                .addValue("batchId", batchId)
                .addValue("reason", reason)
                .addValue("actorName", actorName));
    }
}
