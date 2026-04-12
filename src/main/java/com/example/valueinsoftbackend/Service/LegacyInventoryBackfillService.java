package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class LegacyInventoryBackfillService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public LegacyInventoryBackfillService(JdbcTemplate jdbcTemplate,
                                          NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public void backfillBranchProducts(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");

        if (!legacyProductTableExists(companyId, branchId)) {
            return;
        }

        String legacyTable = TenantSqlIdentifiers.productTable(companyId, branchId);
        String sql = """
                SELECT legacy."productId",
                       legacy."productName",
                       legacy."buyingDay",
                       legacy."activationPeriod",
                       legacy."rPrice",
                       legacy."lPrice",
                       legacy."bPrice",
                       legacy."companyName",
                       legacy.type,
                       legacy."ownerName",
                       legacy.serial,
                       legacy."desc",
                       legacy."batteryLife",
                       legacy."ownerPhone",
                       legacy."ownerNI",
                       legacy.quantity,
                       legacy."pState",
                       legacy."supplierId",
                       legacy.major,
                       legacy."imgFile"
                FROM %s legacy
                LEFT JOIN %s mapping
                  ON mapping.branch_id = :branchId
                 AND mapping.legacy_product_id = legacy."productId"
                WHERE mapping.product_id IS NULL
                ORDER BY legacy."productId" ASC
                """.formatted(legacyTable, TenantSqlIdentifiers.inventoryLegacyProductMappingTable(companyId));

        List<LegacyProductRow> rows = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId),
                (rs, rowNum) -> new LegacyProductRow(
                        rs.getInt("productId"),
                        rs.getString("productName"),
                        rs.getTimestamp("buyingDay"),
                        rs.getInt("activationPeriod"),
                        rs.getInt("rPrice"),
                        rs.getInt("lPrice"),
                        rs.getInt("bPrice"),
                        rs.getString("companyName"),
                        rs.getString("type"),
                        rs.getString("ownerName"),
                        rs.getString("serial"),
                        rs.getString("desc"),
                        rs.getInt("batteryLife"),
                        rs.getString("ownerPhone"),
                        rs.getString("ownerNI"),
                        rs.getInt("quantity"),
                        rs.getString("pState"),
                        rs.getInt("supplierId"),
                        rs.getString("major"),
                        rs.getString("imgFile")
                )
        );

        for (LegacyProductRow row : rows) {
            long productId = insertModernProduct(companyId, row);
            upsertBranchStock(companyId, branchId, productId, row.quantity());
            if (row.quantity() > 0) {
                insertLedger(companyId, branchId, productId, row.quantity(), row.legacyProductId());
            }
            saveMapping(companyId, branchId, row.legacyProductId(), productId);
        }

        if (!rows.isEmpty()) {
            log.info("Backfilled {} legacy products for company {} branch {}", rows.size(), companyId, branchId);
        }
    }

    public Integer resolveModernProductId(int companyId, int branchId, int legacyProductId) {
        List<Integer> result = jdbcTemplate.query(
                "SELECT product_id FROM " + TenantSqlIdentifiers.inventoryLegacyProductMappingTable(companyId) + " WHERE branch_id = ? AND legacy_product_id = ?",
                (rs, rowNum) -> rs.getInt("product_id"),
                branchId,
                legacyProductId
        );
        return result.isEmpty() ? null : result.get(0);
    }

    private boolean legacyProductTableExists(int companyId, int branchId) {
        String tableName = TenantSqlIdentifiers.productTable(companyId, branchId);
        String sql = "SELECT to_regclass(?)";
        String value = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null, tableName);
        return value != null && !value.isBlank();
    }

    private long insertModernProduct(int companyId, LegacyProductRow row) {
        NormalizedLegacyProduct normalized = normalize(row);
        String sql = """
                INSERT INTO %s (
                    product_name, buying_day, activation_period, retail_price, lowest_price, buying_price,
                    company_name, product_type, owner_name, serial, description, battery_life, owner_phone,
                    owner_ni, product_state, supplier_id, major, img_file, business_line_key, template_key, base_uom_code,
                    pricing_policy_code, created_at, updated_at
                ) VALUES (
                    :productName, :buyingDay, :activationPeriod, :retailPrice, :lowestPrice, :buyingPrice,
                    :companyName, :productType, :ownerName, :serial, :description, :batteryLife, :ownerPhone,
                    :ownerNi, :productState, :supplierId, :major, :imgFile, :businessLineKey, :templateKey, :baseUomCode,
                    :pricingPolicyCode, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("productName", normalized.productName())
                        .addValue("buyingDay", normalized.buyingDay())
                        .addValue("activationPeriod", normalized.activationPeriod())
                        .addValue("retailPrice", normalized.retailPrice())
                        .addValue("lowestPrice", normalized.lowestPrice())
                        .addValue("buyingPrice", normalized.buyingPrice())
                        .addValue("companyName", normalized.companyName())
                        .addValue("productType", normalized.productType())
                        .addValue("ownerName", normalized.ownerName())
                        .addValue("serial", normalized.serial())
                        .addValue("description", normalized.description())
                        .addValue("batteryLife", normalized.batteryLife())
                        .addValue("ownerPhone", normalized.ownerPhone())
                        .addValue("ownerNi", normalized.ownerNi())
                        .addValue("productState", normalized.productState())
                        .addValue("supplierId", normalized.supplierId())
                        .addValue("major", normalized.major())
                        .addValue("imgFile", normalized.imgFile())
                        .addValue("businessLineKey", normalized.businessLineKey())
                        .addValue("templateKey", normalized.templateKey())
                        .addValue("baseUomCode", normalized.baseUomCode())
                        .addValue("pricingPolicyCode", normalized.pricingPolicyCode()),
                keyHolder,
                new String[]{"product_id"}
        );
        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("Legacy product backfill could not create a modern product row");
        }
        return keyHolder.getKey().longValue();
    }

    private NormalizedLegacyProduct normalize(LegacyProductRow row) {
        int firstPrice = Math.max(0, row.retailPrice());
        int secondPrice = Math.max(0, row.lowestPrice());
        int thirdPrice = Math.max(0, row.buyingPrice());
        int retailPrice = Math.max(firstPrice, Math.max(secondPrice, thirdPrice));
        int buyingPrice = Math.min(firstPrice, Math.min(secondPrice, thirdPrice));
        int lowestPrice = firstPrice + secondPrice + thirdPrice - retailPrice - buyingPrice;

        return new NormalizedLegacyProduct(
                sanitize(row.productName(), "Legacy Product"),
                row.buyingDay() == null ? new Timestamp(System.currentTimeMillis()) : row.buyingDay(),
                Math.max(0, row.activationPeriod()),
                retailPrice,
                lowestPrice,
                buyingPrice,
                sanitize(row.companyName(), "Unknown"),
                sanitize(row.productType(), "Unknown"),
                sanitizeNullable(row.ownerName()),
                sanitizeNullable(row.serial()),
                sanitizeNullable(row.description()),
                Math.max(0, row.batteryLife()),
                sanitizeNullable(row.ownerPhone()),
                sanitizeNullable(row.ownerNi()),
                normalizeProductState(row.productState()),
                Math.max(0, row.supplierId()),
                sanitize(row.major(), "General"),
                sanitizeNullable(row.imgFile()),
                "MOBILE",
                inferTemplateKey(row.major(), row.productType()),
                "PCS",
                "FIXED_RETAIL"
        );
    }

    private String inferTemplateKey(String major, String productType) {
        String combined = ((major == null ? "" : major) + " " + (productType == null ? "" : productType)).toLowerCase();
        if (combined.contains("access")) {
            return "mobile_accessory";
        }
        return "mobile_device";
    }

    private String sanitize(String value, String fallback) {
        String normalized = sanitizeNullable(value);
        return normalized == null ? fallback : normalized;
    }

    private String sanitizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeProductState(String value) {
        String normalized = sanitizeNullable(value);
        if (normalized == null) {
            return "New";
        }
        return normalized.equalsIgnoreCase("Used") ? "Used" : "New";
    }

    private void upsertBranchStock(int companyId, int branchId, long productId, int quantity) {
        namedParameterJdbcTemplate.update(
                """
                INSERT INTO %s (
                    branch_id, product_id, quantity, reserved_qty, updated_at
                ) VALUES (
                    :branchId, :productId, :quantity, 0, CURRENT_TIMESTAMP
                )
                ON CONFLICT (branch_id, product_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = CURRENT_TIMESTAMP
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId)),
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("quantity", quantity)
        );
    }

    private void insertLedger(int companyId, int branchId, long productId, int quantityDelta, int legacyProductId) {
        namedParameterJdbcTemplate.update(
                """
                INSERT INTO %s (
                    branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id,
                    actor_name, note, created_at
                ) VALUES (
                    :branchId, :productId, :quantityDelta, :movementType, :referenceType, :referenceId,
                    :actorName, :note, CURRENT_TIMESTAMP
                )
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId)),
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("quantityDelta", quantityDelta)
                        .addValue("movementType", "LEGACY_OPENING_BALANCE")
                        .addValue("referenceType", "LEGACY_PRODUCT")
                        .addValue("referenceId", String.valueOf(legacyProductId))
                        .addValue("actorName", "legacy-backfill")
                        .addValue("note", "Migrated from legacy branch product table")
        );
    }

    private void saveMapping(int companyId, int branchId, int legacyProductId, long productId) {
        namedParameterJdbcTemplate.update(
                """
                INSERT INTO %s (
                    branch_id, legacy_product_id, product_id, synced_at
                ) VALUES (
                    :branchId, :legacyProductId, :productId, CURRENT_TIMESTAMP
                )
                ON CONFLICT (branch_id, legacy_product_id)
                DO UPDATE SET product_id = EXCLUDED.product_id, synced_at = CURRENT_TIMESTAMP
                """.formatted(TenantSqlIdentifiers.inventoryLegacyProductMappingTable(companyId)),
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("legacyProductId", legacyProductId)
                        .addValue("productId", productId)
        );
    }

    private record LegacyProductRow(
            int legacyProductId,
            String productName,
            Timestamp buyingDay,
            int activationPeriod,
            int retailPrice,
            int lowestPrice,
            int buyingPrice,
            String companyName,
            String productType,
            String ownerName,
            String serial,
            String description,
            int batteryLife,
            String ownerPhone,
            String ownerNi,
            int quantity,
            String productState,
            int supplierId,
            String major,
            String imgFile
    ) {
    }

    private record NormalizedLegacyProduct(
            String productName,
            Timestamp buyingDay,
            int activationPeriod,
            int retailPrice,
            int lowestPrice,
            int buyingPrice,
            String companyName,
            String productType,
            String ownerName,
            String serial,
            String description,
            int batteryLife,
            String ownerPhone,
            String ownerNi,
            String productState,
            int supplierId,
            String major,
            String imgFile,
            String businessLineKey,
            String templateKey,
            String baseUomCode,
            String pricingPolicyCode
    ) {
    }
}
