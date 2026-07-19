package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbInventorySupplierReturnRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DbInventorySupplierReturnRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertCompatibilityReturn(int companyId,
                                          int branchId,
                                          long productId,
                                          int supplierId,
                                          int quantity,
                                          int unitCost,
                                          int refundAmount,
                                          String actorName,
                                          String note) {
        String sql = """
                INSERT INTO %s (
                    "productId", quantity, cost, "userName", "sPaid", "time", "desc",
                    "orderDetailsId", "supplierId", "branchId"
                ) VALUES (
                    :productId, :quantity, :unitCost, :actorName, :refundAmount, CURRENT_TIMESTAMP, :note,
                    0, :supplierId, :branchId
                )
                RETURNING "sBPId"
                """.formatted(TenantSqlIdentifiers.supplierBoughtProductTable(companyId));
        List<Long> ids = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("quantity", quantity)
                .addValue("unitCost", unitCost)
                .addValue("actorName", actorName)
                .addValue("refundAmount", refundAmount)
                .addValue("note", note)
                .addValue("supplierId", supplierId)
                .addValue("branchId", branchId), (rs, rowNum) -> rs.getLong("sBPId"));
        return ids.isEmpty() ? 0 : ids.getFirst();
    }

    public Optional<SupplierAccountSnapshot> applySupplierReturn(int companyId,
                                                                  int branchId,
                                                                  int supplierId,
                                                                  int returnAmount,
                                                                  int payableCreditAmount) {
        String sql = """
                UPDATE %s
                SET "supplierTotalSales" = GREATEST(0, COALESCE("supplierTotalSales", 0) - :returnAmount),
                    "supplierRemainig" = COALESCE("supplierRemainig", 0) - :payableCreditAmount
                WHERE "supplierId" = :supplierId
                RETURNING "supplierTotalSales", "supplierRemainig"
                """.formatted(TenantSqlIdentifiers.supplierTable(companyId, branchId));
        List<SupplierAccountSnapshot> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("supplierId", supplierId)
                .addValue("returnAmount", returnAmount)
                .addValue("payableCreditAmount", payableCreditAmount), (rs, rowNum) -> new SupplierAccountSnapshot(
                rs.getInt("supplierTotalSales"),
                rs.getInt("supplierRemainig")
        ));
        return rows.stream().findFirst();
    }

    public record SupplierAccountSnapshot(int totalPurchases, int payableBalance) {
    }
}
