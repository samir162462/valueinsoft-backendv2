package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import com.example.valueinsoftbackend.Model.Inventory.ProductTrackingMetadata;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class DbInventoryProductTrackingRepository {

    private static final RowMapper<ProductTrackingMetadata> TRACKING_ROW_MAPPER = (rs, rowNum) -> new ProductTrackingMetadata(
            toTrackingType(rs.getString("tracking_type")),
            rs.getString("sku"),
            rs.getString("barcode"),
            getLongOrNull(rs, "version")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbInventoryProductTrackingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProductTrackingMetadata> findTrackingMetadata(long companyId, long productId) {
        String sql = """
                SELECT tracking_type, sku, barcode, version
                FROM %s
                WHERE company_id = :companyId
                  AND product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("companyId", companyId)
                            .addValue("productId", productId),
                    TRACKING_ROW_MAPPER
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public int updateTrackingMetadata(long companyId,
                                      long productId,
                                      TrackingType trackingType,
                                      String sku,
                                      String barcode) {
        String sql = """
                UPDATE %s
                SET tracking_type = :trackingType,
                    sku = COALESCE(:sku, sku),
                    barcode = COALESCE(:barcode, barcode),
                    version = version + 1
                WHERE company_id = :companyId
                  AND product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("productId", productId)
                        .addValue("trackingType", trackingType.name())
                        .addValue("sku", blankToNull(sku))
                        .addValue("barcode", blankToNull(barcode))
        );
    }

    public long countSerializedUnits(long companyId, long productId) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE company_id = :companyId
                  AND product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductUnitTable(companyId));

        Long count = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("companyId", companyId)
                        .addValue("productId", productId),
                Long.class
        );
        return count == null ? 0 : count;
    }

    public long countNonZeroQuantityBalance(long companyId, long branchId, long productId) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE company_id = :companyId
                  AND branch_id = :branchId
                  AND product_id = :productId
                  AND (
                    COALESCE(quantity, 0) <> 0
                    OR COALESCE(reserved_qty, 0) <> 0
                  )
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));

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

    private static String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static TrackingType toTrackingType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TrackingType.QUANTITY;
        }
        return TrackingType.valueOf(value.trim().toUpperCase());
    }

    private static Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
