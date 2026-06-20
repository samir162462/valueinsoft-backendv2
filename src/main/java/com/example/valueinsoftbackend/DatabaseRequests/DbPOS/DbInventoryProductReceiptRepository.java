package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DbInventoryProductReceiptRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DbInventoryProductReceiptRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean insertPendingIdempotency(int companyId,
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

        int rows = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("operationType", operationType)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("requestHash", requestHash)
                .addValue("actorName", actorName)
                .addValue("operationId", operationId));
        return rows == 1;
    }

    public Optional<IdempotencyRecord> findIdempotencyForUpdate(int companyId,
                                                                int branchId,
                                                                String operationType,
                                                                String idempotencyKey) {
        String sql = """
                SELECT id, company_id, branch_id, operation_type, idempotency_key, request_hash,
                       status, response_payload, operation_id
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND operation_type = :operationType
                  AND idempotency_key = :idempotencyKey
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryOperationIdempotencyTable(companyId));

        List<IdempotencyRecord> records = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("operationType", operationType)
                .addValue("idempotencyKey", idempotencyKey), idempotencyMapper());
        return records.stream().findFirst();
    }

    public void markIdempotencyCompleted(int companyId,
                                         long id,
                                         String responsePayload) {
        String sql = """
                UPDATE %s
                SET status = 'COMPLETED',
                    response_payload = CAST(:responsePayload AS jsonb),
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """.formatted(TenantSqlIdentifiers.inventoryOperationIdempotencyTable(companyId));
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("responsePayload", responsePayload));
    }

    public Optional<ProductReceiptProductSnapshot> findProductForUpdate(int companyId, long productId) {
        String sql = """
                SELECT product_id, product_name, sku, barcode, tracking_type, business_line_key, template_key
                FROM %s
                WHERE company_id = :companyId
                  AND product_id = :productId
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));
        List<ProductReceiptProductSnapshot> rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("productId", productId), productMapper());
        return rows.stream().findFirst();
    }

    public Optional<InventoryTemplateDefinition> findActiveTemplate(int companyId, String businessLineKey, String templateKey) {
        String sql = """
                SELECT template_id, business_line_key, template_key, supports_serial, supports_batch,
                       supports_expiry, supports_weight, is_active
                FROM %s
                WHERE business_line_key = :businessLineKey
                  AND template_key = :templateKey
                  AND is_active = TRUE
                """.formatted(TenantSqlIdentifiers.inventoryProductTemplateTable(companyId));
        List<InventoryTemplateDefinition> rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("businessLineKey", businessLineKey)
                .addValue("templateKey", templateKey), templateMapper());
        return rows.stream().findFirst();
    }

    public List<InventoryTemplateAttributeDefinition> findTemplateAttributes(int companyId, long templateId) {
        String sql = """
                SELECT attr.attribute_key, attr.data_type, COALESCE(template_attr.is_required, attr.is_required) AS is_required,
                       attr.field_schema
                FROM %s template_attr
                JOIN %s attr ON attr.attribute_id = template_attr.attribute_id
                WHERE template_attr.template_id = :templateId
                """.formatted(
                TenantSqlIdentifiers.inventoryTemplateAttributeTable(companyId),
                TenantSqlIdentifiers.inventoryAttributeDefinitionTable(companyId));
        return jdbcTemplate.query(sql, new MapSqlParameterSource().addValue("templateId", templateId), attributeMapper());
    }

    public StockBalanceResult increaseBranchStockBalance(int companyId, int branchId, long productId, int quantityDelta) {
        String sql = """
                INSERT INTO %s (company_id, branch_id, product_id, quantity, reserved_qty, updated_at, version)
                VALUES (:companyId, :branchId, :productId, :quantityDelta, 0, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (branch_id, product_id)
                DO UPDATE SET quantity = %s.quantity + EXCLUDED.quantity,
                              updated_at = CURRENT_TIMESTAMP,
                              version = %s.version + 1
                RETURNING (quantity - :quantityDelta)::integer AS previous_quantity, quantity::integer AS new_quantity
                """.formatted(
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId),
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId),
                TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", productId)
                .addValue("quantityDelta", quantityDelta),
                (rs, rowNum) -> new StockBalanceResult(rs.getInt("previous_quantity"), rs.getInt("new_quantity")));
    }

    public long insertReceiptLedger(int companyId,
                                    int branchId,
                                    long productId,
                                    int quantityDelta,
                                    int supplierId,
                                    BigDecimal transTotal,
                                    String payType,
                                    BigDecimal remainingAmount,
                                    String actorName,
                                    String referenceId,
                                    String idempotencyKey,
                                    String note) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, product_id, quantity_delta, movement_type,
                    reference_type, reference_id, actor_name, note, supplier_id, trans_total,
                    pay_type, remaining_amount, idempotency_key, created_at
                ) VALUES (
                    :companyId, :branchId, :productId, :quantityDelta, 'PURCHASE_RECEIPT',
                    'PRODUCT_RECEIPT', :referenceId, :actorName, :note, :supplierId, :transTotal,
                    :payType, :remainingAmount, :idempotencyKey, CURRENT_TIMESTAMP
                )
                RETURNING stock_ledger_id
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("branchId", branchId)
                .addValue("productId", productId)
                .addValue("quantityDelta", quantityDelta)
                .addValue("referenceId", referenceId)
                .addValue("actorName", actorName)
                .addValue("note", note)
                .addValue("supplierId", supplierId)
                .addValue("transTotal", transTotal == null ? BigDecimal.ZERO : transTotal)
                .addValue("payType", payType)
                .addValue("remainingAmount", remainingAmount == null ? BigDecimal.ZERO : remainingAmount)
                .addValue("idempotencyKey", idempotencyKey), Long.class);
    }

    public int insertLegacyInventoryTransaction(int companyId,
                                                int branchId,
                                                long productId,
                                                String actorName,
                                                int supplierId,
                                                int quantity,
                                                BigDecimal totalCost,
                                                String payType,
                                                Timestamp receiptTime,
                                                BigDecimal remainingAmount) {
        String sql = """
                INSERT INTO %s ("productId", "userName", "supplierId", "transactionType", "NumItems", "transTotal", "payType", "time", "RemainingAmount")
                VALUES (:productId, :actorName, :supplierId, 'PurchaseReceipt', :quantity, :totalCost, :payType, :receiptTime, :remainingAmount)
                """.formatted(TenantSqlIdentifiers.inventoryTransactionsTable(companyId, branchId));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("actorName", limit(actorName, 15))
                .addValue("supplierId", supplierId)
                .addValue("quantity", quantity)
                .addValue("totalCost", moneyToInt(totalCost))
                .addValue("payType", payType)
                .addValue("receiptTime", receiptTime)
                .addValue("remainingAmount", moneyToInt(remainingAmount)), keyHolder, new String[]{"transId"});
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.intValue();
    }

    public int updateSupplierPurchaseTotals(int companyId,
                                            int branchId,
                                            int supplierId,
                                            BigDecimal totalCost,
                                            BigDecimal remainingAmount) {
        String sql = """
                UPDATE %s
                SET "supplierTotalSales" = "supplierTotalSales" + :totalCost,
                    "supplierRemainig" = "supplierRemainig" + :remainingAmount
                WHERE "supplierId" = :supplierId
                """.formatted(TenantSqlIdentifiers.supplierTable(companyId, branchId));
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("supplierId", supplierId)
                .addValue("totalCost", moneyToInt(totalCost))
                .addValue("remainingAmount", moneyToInt(remainingAmount)));
    }

    public record IdempotencyRecord(long id, String requestHash, String status, JsonNode responsePayload, String operationId) {}
    public record StockBalanceResult(int previousQuantity, int newQuantity) {}
    public record ProductReceiptProductSnapshot(long productId, String productName, String sku, String barcode,
                                                TrackingType trackingType, String businessLineKey, String templateKey) {}
    public record InventoryTemplateDefinition(long templateId, String businessLineKey, String templateKey,
                                              boolean supportsSerial, boolean supportsBatch, boolean supportsExpiry,
                                              boolean supportsWeight, boolean active) {}
    public record InventoryTemplateAttributeDefinition(String attributeKey, String dataType, boolean required, JsonNode fieldSchema) {}

    private RowMapper<IdempotencyRecord> idempotencyMapper() {
        return (rs, rowNum) -> new IdempotencyRecord(
                rs.getLong("id"),
                rs.getString("request_hash"),
                rs.getString("status"),
                jsonNode(rs.getObject("response_payload")),
                rs.getString("operation_id")
        );
    }

    private RowMapper<ProductReceiptProductSnapshot> productMapper() {
        return (rs, rowNum) -> new ProductReceiptProductSnapshot(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getString("barcode"),
                TrackingType.defaultIfNull(TrackingType.valueOf(rs.getString("tracking_type"))),
                rs.getString("business_line_key"),
                rs.getString("template_key")
        );
    }

    private RowMapper<InventoryTemplateDefinition> templateMapper() {
        return (rs, rowNum) -> new InventoryTemplateDefinition(
                rs.getLong("template_id"),
                rs.getString("business_line_key"),
                rs.getString("template_key"),
                rs.getBoolean("supports_serial"),
                rs.getBoolean("supports_batch"),
                rs.getBoolean("supports_expiry"),
                rs.getBoolean("supports_weight"),
                rs.getBoolean("is_active")
        );
    }

    private RowMapper<InventoryTemplateAttributeDefinition> attributeMapper() {
        return (rs, rowNum) -> new InventoryTemplateAttributeDefinition(
                rs.getString("attribute_key"),
                rs.getString("data_type"),
                rs.getBoolean("is_required"),
                jsonNode(rs.getObject("field_schema"))
        );
    }


    private JsonNode jsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        try {
            return objectMapper.readTree(value.toString());
        } catch (Exception exception) {
            return null;
        }
    }
    private int moneyToInt(BigDecimal value) {
        return value == null ? 0 : value.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "system";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "system";
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}


