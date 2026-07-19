package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DbInventoryAdjustmentRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DbInventoryAdjustmentRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insertPendingIdempotency(int companyId,
                                         int branchId,
                                         String operationType,
                                         String idempotencyKey,
                                         String requestHash,
                                         String actorName,
                                         String operationId) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, operation_type, idempotency_key, request_hash,
                    status, response_payload, created_by, operation_id, created_at
                ) VALUES (
                    :companyId, :branchId, :operationType, :idempotencyKey, :requestHash,
                    'PENDING', NULL, :actorName, :operationId, CURRENT_TIMESTAMP
                )
                ON CONFLICT (company_id, branch_id, operation_type, idempotency_key) DO NOTHING
                """.formatted(TenantSqlIdentifiers.inventoryOperationIdempotencyTable(companyId));
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("operationType", operationType)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("requestHash", requestHash)
                .addValue("actorName", actorName)
                .addValue("operationId", operationId));
    }

    public Optional<IdempotencyRecord> findIdempotencyForUpdate(int companyId,
                                                                 int branchId,
                                                                 String operationType,
                                                                 String idempotencyKey) {
        String sql = """
                SELECT id, request_hash, status, response_payload, operation_id
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND operation_type = :operationType
                  AND idempotency_key = :idempotencyKey
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryOperationIdempotencyTable(companyId));
        List<IdempotencyRecord> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("operationType", operationType)
                .addValue("idempotencyKey", idempotencyKey), (rs, rowNum) -> new IdempotencyRecord(
                rs.getLong("id"),
                rs.getString("request_hash"),
                rs.getString("status"),
                jsonNode(rs.getObject("response_payload")),
                rs.getString("operation_id")
        ));
        return rows.stream().findFirst();
    }

    public void markIdempotencyCompleted(int companyId, long id, String responsePayload) {
        String sql = """
                UPDATE %s
                SET status = 'COMPLETED', response_payload = CAST(:responsePayload AS jsonb),
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """.formatted(TenantSqlIdentifiers.inventoryOperationIdempotencyTable(companyId));
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("responsePayload", responsePayload));
    }

    public Optional<ProductSnapshot> findProductForUpdate(int companyId, long productId) {
        String sql = """
                SELECT product_id, product_name, buying_price,
                       COALESCE(tracking_type, 'QUANTITY') AS tracking_type
                FROM %s
                WHERE company_id = :companyId AND product_id = :productId
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        List<ProductSnapshot> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("productId", productId), (rs, rowNum) -> new ProductSnapshot(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getInt("buying_price"),
                TrackingType.defaultIfNull(TrackingType.valueOf(rs.getString("tracking_type")))
        ));
        return rows.stream().findFirst();
    }

    public Optional<SupplierReturnProductSnapshot> findSupplierReturnProductForUpdate(int companyId, long productId) {
        String sql = """
                SELECT product_id, product_name, buying_price, COALESCE(supplier_id, 0) AS supplier_id,
                       COALESCE(tracking_type, 'QUANTITY') AS tracking_type
                FROM %s
                WHERE company_id = :companyId AND product_id = :productId
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        List<SupplierReturnProductSnapshot> rows = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("productId", productId), (rs, rowNum) -> new SupplierReturnProductSnapshot(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getInt("buying_price"),
                rs.getInt("supplier_id"),
                TrackingType.defaultIfNull(TrackingType.valueOf(rs.getString("tracking_type")))
        ));
        return rows.stream().findFirst();
    }

    public Optional<BalanceSnapshot> findBalanceForUpdate(int companyId, int branchId, long productId) {
        String sql = """
                SELECT quantity, reserved_qty, version
                FROM %s
                WHERE company_id = :companyId AND branch_id = :branchId AND product_id = :productId
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));
        List<BalanceSnapshot> rows = jdbc.query(sql, params(companyId, branchId, productId), (rs, rowNum) ->
                new BalanceSnapshot(rs.getInt("quantity"), rs.getInt("reserved_qty"), rs.getLong("version")));
        return rows.stream().findFirst();
    }

    public Optional<BalanceSnapshot> insertBalance(int companyId,
                                                    int branchId,
                                                    long productId,
                                                    int quantity) {
        String sql = """
                INSERT INTO %s (company_id, branch_id, product_id, quantity, reserved_qty, updated_at, version)
                VALUES (:companyId, :branchId, :productId, :quantity, 0, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (branch_id, product_id) DO NOTHING
                RETURNING quantity, reserved_qty, version
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));
        List<BalanceSnapshot> rows = jdbc.query(sql, params(companyId, branchId, productId).addValue("quantity", quantity),
                (rs, rowNum) -> new BalanceSnapshot(rs.getInt("quantity"), rs.getInt("reserved_qty"), rs.getLong("version")));
        return rows.stream().findFirst();
    }

    public Optional<BalanceSnapshot> updateBalance(int companyId,
                                                    int branchId,
                                                    long productId,
                                                    int quantityDelta,
                                                    long expectedVersion) {
        String sql = """
                UPDATE %s
                SET quantity = quantity + :quantityDelta,
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND version = :expectedVersion
                  AND quantity + :quantityDelta >= reserved_qty
                RETURNING quantity, reserved_qty, version
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));
        List<BalanceSnapshot> rows = jdbc.query(sql, params(companyId, branchId, productId)
                        .addValue("quantityDelta", quantityDelta)
                        .addValue("expectedVersion", expectedVersion),
                (rs, rowNum) -> new BalanceSnapshot(rs.getInt("quantity"), rs.getInt("reserved_qty"), rs.getLong("version")));
        return rows.stream().findFirst();
    }

    private MapSqlParameterSource params(int companyId, int branchId, long productId) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", productId);
    }

    private JsonNode jsonNode(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode node) return node;
        try {
            return objectMapper.readTree(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    public record IdempotencyRecord(long id, String requestHash, String status, JsonNode responsePayload,
                                    String operationId) {
    }

    public record ProductSnapshot(long productId, String productName, int buyingPrice, TrackingType trackingType) {
    }

    public record SupplierReturnProductSnapshot(long productId, String productName, int buyingPrice,
                                                int supplierId, TrackingType trackingType) {
    }

    public record BalanceSnapshot(int quantity, int reservedQuantity, long version) {
    }
}
