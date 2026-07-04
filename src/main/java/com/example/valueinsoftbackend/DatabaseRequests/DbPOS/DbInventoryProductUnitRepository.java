package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Inventory.ProductUnit;
import com.example.valueinsoftbackend.Model.Inventory.ProductUnitStatus;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class DbInventoryProductUnitRepository {

    private static final RowMapper<ProductUnit> PRODUCT_UNIT_ROW_MAPPER = (rs, rowNum) -> new ProductUnit(
            rs.getLong("product_unit_id"),
            rs.getLong("company_id"),
            rs.getLong("branch_id"),
            rs.getLong("product_id"),
            TrackingType.valueOf(rs.getString("tracking_type")),
            rs.getString("unit_identifier"),
            rs.getString("imei"),
            rs.getString("serial_number"),
            ProductUnitStatus.valueOf(rs.getString("status")),
            rs.getString("condition_code"),
            getLongOrNull(rs, "supplier_id"),
            rs.getString("purchase_reference_type"),
            rs.getString("purchase_reference_id"),
            getLongOrNull(rs, "purchase_line_id"),
            getLongOrNull(rs, "sale_order_id"),
            getLongOrNull(rs, "sale_order_detail_id"),
            getLongOrNull(rs, "customer_id"),
            getLongOrNull(rs, "current_transfer_id"),
            rs.getTimestamp("received_at"),
            rs.getTimestamp("sold_at"),
            rs.getTimestamp("returned_at"),
            rs.getTimestamp("status_updated_at"),
            rs.getTimestamp("created_at"),
            rs.getTimestamp("updated_at"),
            rs.getLong("version")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbInventoryProductUnitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertProductUnit(ProductUnit productUnit) {
        String sql = """
                INSERT INTO %s (
                    company_id, branch_id, product_id, tracking_type, unit_identifier,
                    imei, serial_number, status, condition_code, supplier_id,
                    purchase_reference_type, purchase_reference_id, purchase_line_id,
                    received_at, status_updated_at, created_at, updated_at, version
                ) VALUES (
                    :companyId, :branchId, :productId, :trackingType, :unitIdentifier,
                    :imei, :serialNumber, :status, :conditionCode, :supplierId,
                    :purchaseReferenceType, :purchaseReferenceId, :purchaseLineId,
                    :receivedAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(productUnit.getCompanyId()));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, toInsertParams(productUnit), keyHolder, new String[]{"product_unit_id"});
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("Product unit insert did not return product_unit_id");
        }
        return keyHolder.getKey().longValue();
    }

    public Optional<ProductUnit> findById(long companyId, long productUnitId) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND product_unit_id = :productUnitId
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("companyId", companyId)
                            .addValue("productUnitId", productUnitId),
                    PRODUCT_UNIT_ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<ProductUnit> findByScanCode(long companyId, long branchId, String scanCode) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND (
                    lower(unit_identifier) = lower(:scanCode)
                    OR lower(imei) = lower(:scanCode)
                    OR lower(serial_number) = lower(:scanCode)
                  )
                ORDER BY product_unit_id DESC
                LIMIT 1
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("companyId", companyId)
                            .addValue("branchId", branchId)
                            .addValue("scanCode", scanCode),
                    PRODUCT_UNIT_ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Optional<ProductUnit> findAvailableForSaleForUpdate(long companyId,
                                                               long branchId,
                                                               long productId,
                                                               long productUnitId) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND product_unit_id = :productUnitId
                  AND status = 'AVAILABLE'
                FOR UPDATE
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("companyId", companyId)
                            .addValue("branchId", branchId)
                            .addValue("productId", productId)
                            .addValue("productUnitId", productUnitId),
                    PRODUCT_UNIT_ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public int markSold(long companyId,
                        long branchId,
                        long productId,
                        long productUnitId,
                        long orderId,
                        Long orderDetailId,
                        Long customerId) {
        String sql = """
                UPDATE %s
                SET status = 'SOLD',
                    sale_order_id = :orderId,
                    sale_order_detail_id = :orderDetailId,
                    customer_id = :customerId,
                    sold_at = CURRENT_TIMESTAMP,
                    status_updated_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND product_unit_id = :productUnitId
                  AND status = 'AVAILABLE'
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("productUnitId", productUnitId)
                        .addValue("orderId", orderId)
                        .addValue("orderDetailId", orderDetailId)
                        .addValue("customerId", customerId)
        );
    }

    public int updateStatus(long companyId,
                            long branchId,
                            long productUnitId,
                            ProductUnitStatus fromStatus,
                            ProductUnitStatus toStatus,
                            Long toBranchId) {
        String sql = """
                UPDATE %s
                SET status = :toStatus,
                    branch_id = COALESCE(:toBranchId, branch_id),
                    returned_at = CASE WHEN :toStatus = 'RETURNED' THEN CURRENT_TIMESTAMP ELSE returned_at END,
                    status_updated_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_unit_id = :productUnitId
                  AND status = :fromStatus
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productUnitId", productUnitId)
                        .addValue("fromStatus", fromStatus.name())
                        .addValue("toStatus", toStatus.name())
                        .addValue("toBranchId", toBranchId)
        );
    }

    /**
     * Corrects the identifier of an existing serialized unit. Only units that are
     * still AVAILABLE in the given branch/product can be edited, protecting sold or
     * transferred units from silent identifier changes. Uniqueness is enforced by the
     * table's unique index (a collision surfaces as a DuplicateKeyException).
     */
    public int updateUnitIdentifier(long companyId,
                                    long branchId,
                                    long productId,
                                    long productUnitId,
                                    String imei,
                                    String serialNumber,
                                    String unitIdentifier,
                                    String conditionCode) {
        String sql = """
                UPDATE %s
                SET imei = :imei,
                    serial_number = :serialNumber,
                    unit_identifier = :unitIdentifier,
                    condition_code = COALESCE(:conditionCode, condition_code),
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND product_unit_id = :productUnitId
                  AND status = 'AVAILABLE'
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("productUnitId", productUnitId)
                        .addValue("imei", imei)
                        .addValue("serialNumber", serialNumber)
                        .addValue("unitIdentifier", unitIdentifier)
                        .addValue("conditionCode", conditionCode)
        );
    }

    public long countAvailableByProduct(long companyId, long branchId, long productId) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND status = 'AVAILABLE'
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        Long count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId),
                Long.class
        );
        return count == null ? 0 : count;
    }

    public List<ProductUnit> listByProductBranchAndStatus(long companyId,
                                                          long branchId,
                                                          long productId,
                                                          ProductUnitStatus status) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
                ORDER BY product_unit_id DESC
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("status", status == null ? null : status.name()),
                PRODUCT_UNIT_ROW_MAPPER
        );
    }

    public List<ProductUnit> findBySaleOrderDetail(long companyId,
                                                   long branchId,
                                                   long productId,
                                                   long saleOrderDetailId) {
        String sql = """
                SELECT *
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND sale_order_detail_id = :saleOrderDetailId
                ORDER BY product_unit_id ASC
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("saleOrderDetailId", saleOrderDetailId),
                PRODUCT_UNIT_ROW_MAPPER
        );
    }

    private MapSqlParameterSource toInsertParams(ProductUnit productUnit) {
        TrackingType trackingType = TrackingType.defaultIfNull(productUnit.getTrackingType());
        ProductUnitStatus status = productUnit.getStatus() == null ? ProductUnitStatus.AVAILABLE : productUnit.getStatus();
        Timestamp receivedAt = productUnit.getReceivedAt() == null
                ? new Timestamp(System.currentTimeMillis())
                : productUnit.getReceivedAt();

        return new MapSqlParameterSource()
                .addValue("companyId", productUnit.getCompanyId())
                .addValue("branchId", productUnit.getBranchId())
                .addValue("productId", productUnit.getProductId())
                .addValue("trackingType", trackingType.name())
                .addValue("unitIdentifier", productUnit.getUnitIdentifier())
                .addValue("imei", productUnit.getImei())
                .addValue("serialNumber", productUnit.getSerialNumber())
                .addValue("status", status.name())
                .addValue("conditionCode", productUnit.getConditionCode() == null ? "NEW" : productUnit.getConditionCode())
                .addValue("supplierId", productUnit.getSupplierId())
                .addValue("purchaseReferenceType", productUnit.getPurchaseReferenceType())
                .addValue("purchaseReferenceId", productUnit.getPurchaseReferenceId())
                .addValue("purchaseLineId", productUnit.getPurchaseLineId())
                .addValue("receivedAt", receivedAt);
    }

    private static Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
