package com.example.valueinsoftbackend.DatabaseRequests.DbPOS;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class DbPosProductCommandRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public DbPosProductCommandRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long addProduct(Product product, String branchId, int companyId) {
        int numericBranchId = Integer.parseInt(branchId);
        ProductMetadata metadata = resolveMetadata(product);
        String sql = """
                INSERT INTO %s (
                    product_name, buying_day, activation_period, retail_price, lowest_price, buying_price,
                    company_name, product_type, owner_name, serial, description, battery_life, owner_phone,
                    owner_ni, product_state, supplier_id, major, img_file, business_line_key, template_key, base_uom_code,
                    pricing_policy_code, created_at, updated_at
                ) VALUES (
                    :productName, :buyingDay, :activationPeriod, :rPrice, :lPrice, :bPrice,
                    :companyName, :type, :ownerName, :serial, :description, :batteryLife, :ownerPhone,
                    :ownerNI, :productState, :supplierId, :major, :image, :businessLineKey, :templateKey, :baseUomCode,
                    :pricingPolicyCode, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        MapSqlParameterSource params = createProductParams(product, companyId, metadata);
        params.addValue("buyingDay", resolveBuyingDay(product));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int affectedRows = jdbcTemplate.update(sql, params, keyHolder, new String[]{"product_id"});
        if (affectedRows == 0 || keyHolder.getKey() == null) {
            throw new IllegalStateException("Creating product failed");
        }

        long productId = keyHolder.getKey().longValue();
        upsertBranchQuantity(companyId, numericBranchId, productId, product.getQuantity());
        saveAttributeValues(companyId, productId, metadata.templateKey(), parseAttributes(product.getAttributes()));
        insertLedgerEntry(companyId, numericBranchId, productId, product.getQuantity(), "OPENING_BALANCE", "PRODUCT_CREATE", String.valueOf(productId));
        log.debug("Created company-scoped product {} for company {} branch {}", productId, companyId, branchId);
        return productId;
    }

    public void updateProduct(Product product, String branchId, int companyId) {
        int numericBranchId = Integer.parseInt(branchId);
        int previousQuantity = getCurrentQuantity(companyId, numericBranchId, product.getProductId());
        ProductMetadata metadata = resolveMetadata(product);

        String sql = """
                UPDATE %s
                SET product_name = :productName,
                    buying_day = :buyingDay,
                    activation_period = :activationPeriod,
                    retail_price = :rPrice,
                    lowest_price = :lPrice,
                    buying_price = :bPrice,
                    company_name = :companyName,
                    product_type = :type,
                    owner_name = :ownerName,
                    serial = :serial,
                    description = :description,
                    battery_life = :batteryLife,
                    owner_phone = :ownerPhone,
                    owner_ni = :ownerNI,
                    product_state = :productState,
                    supplier_id = :supplierId,
                    major = :major,
                    img_file = :image,
                    business_line_key = :businessLineKey,
                    template_key = :templateKey,
                    base_uom_code = :baseUomCode,
                    pricing_policy_code = :pricingPolicyCode,
                    updated_at = CURRENT_TIMESTAMP
                WHERE product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryProductTable(companyId));

        MapSqlParameterSource params = createProductParams(product, companyId, metadata);
        params.addValue("buyingDay", product.getBuyingDay());
        params.addValue("productId", product.getProductId());

        int affectedRows = jdbcTemplate.update(sql, params);
        if (affectedRows == 0) {
            throw new IllegalStateException("Updating product failed");
        }

        upsertBranchQuantity(companyId, numericBranchId, product.getProductId(), product.getQuantity());
        saveAttributeValues(companyId, product.getProductId(), metadata.templateKey(), parseAttributes(product.getAttributes()));

        int delta = product.getQuantity() - previousQuantity;
        if (delta != 0) {
            insertLedgerEntry(
                    companyId,
                    numericBranchId,
                    product.getProductId(),
                    delta,
                    delta > 0 ? "MANUAL_STOCK_IN" : "MANUAL_STOCK_OUT",
                    "PRODUCT_EDIT",
                    String.valueOf(product.getProductId())
            );
        }
    }

    private void upsertBranchQuantity(int companyId, int branchId, long productId, int quantity) {
        String sql = """
                INSERT INTO %s (
                    branch_id, product_id, quantity, reserved_qty, updated_at
                ) VALUES (
                    :branchId, :productId, :quantity, 0, CURRENT_TIMESTAMP
                )
                ON CONFLICT (branch_id, product_id)
                DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = CURRENT_TIMESTAMP
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("quantity", quantity)
        );
    }

    private void insertLedgerEntry(int companyId, int branchId, long productId, int quantityDelta,
                                   String movementType, String referenceType, String referenceId) {
        String sql = """
                INSERT INTO %s (
                    branch_id, product_id, quantity_delta, movement_type, reference_type, reference_id,
                    actor_name, note, supplier_id, trans_total, pay_type, remaining_amount, created_at
                ) VALUES (
                    :branchId, :productId, :quantityDelta, :movementType, :referenceType, :referenceId,
                    :actorName, :note, :supplierId, :transTotal, :payType, :remainingAmount, CURRENT_TIMESTAMP
                )
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId));

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", productId)
                        .addValue("quantityDelta", quantityDelta)
                        .addValue("movementType", movementType)
                        .addValue("referenceType", referenceType)
                        .addValue("referenceId", referenceId)
                        .addValue("actorName", "inventory-modernization")
                        .addValue("note", "Initial stage company-catalog stock event")
                        .addValue("supplierId", 0)
                        .addValue("transTotal", 0)
                        .addValue("payType", null)
                        .addValue("remainingAmount", 0)
        );
    }

    private int getCurrentQuantity(int companyId, int branchId, int productId) {
        String sql = """
                SELECT quantity
                FROM %s
                WHERE branch_id = :branchId
                  AND product_id = :productId
                """.formatted(TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId));

        Integer quantity = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("branchId", branchId)
                        .addValue("productId", productId),
                rs -> rs.next() ? rs.getInt("quantity") : 0
        );

        return quantity == null ? 0 : quantity;
    }

    private MapSqlParameterSource createProductParams(Product product, int companyId, ProductMetadata metadata) {
        return new MapSqlParameterSource()
                .addValue("productName", product.getProductName())
                .addValue("activationPeriod", parseActivationPeriod(product.getActivationPeriod()))
                .addValue("rPrice", product.getRPrice())
                .addValue("lPrice", product.getLPrice())
                .addValue("bPrice", product.getBPrice())
                .addValue("companyName", product.getCompanyName())
                .addValue("type", product.getType())
                .addValue("ownerName", product.getOwnerName())
                .addValue("serial", product.getSerial())
                .addValue("description", product.getDesc())
                .addValue("batteryLife", product.getBatteryLife())
                .addValue("ownerPhone", product.getOwnerPhone())
                .addValue("ownerNI", product.getOwnerNI())
                .addValue("quantity", product.getQuantity())
                .addValue("productState", product.getPState())
                .addValue("supplierId", product.getSupplierId())
                .addValue("major", product.getMajor())
                .addValue("image", product.getImage())
                .addValue("businessLineKey", metadata.businessLineKey())
                .addValue("templateKey", metadata.templateKey())
                .addValue("baseUomCode", metadata.baseUomCode())
                .addValue("pricingPolicyCode", metadata.pricingPolicyCode());
    }

    private int parseActivationPeriod(String activationPeriod) {
        if (activationPeriod == null || activationPeriod.isBlank()) {
            return 0;
        }
        return Integer.parseInt(activationPeriod.trim());
    }

    private Timestamp resolveBuyingDay(Product product) {
        if (product.getBuyingDay() != null) {
            return product.getBuyingDay();
        }

        return new Timestamp(System.currentTimeMillis());
    }

    private ProductMetadata resolveMetadata(Product product) {
        String businessLineKey = normalizeBusinessLineKey(product.getBusinessLineKey());
        String templateKey = normalizeTemplateKey(product.getTemplateKey(), product.getMajor(), product.getType(), businessLineKey);
        String baseUomCode = normalizeBaseUomCode(product.getBaseUomCode(), businessLineKey);
        String pricingPolicyCode = normalizePricingPolicyCode(product.getPricingPolicyCode(), businessLineKey);
        return new ProductMetadata(businessLineKey, templateKey, baseUomCode, pricingPolicyCode);
    }

    private String normalizeBusinessLineKey(String businessLineKey) {
        if (businessLineKey == null || businessLineKey.isBlank()) {
            return "MOBILE";
        }
        return businessLineKey.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    private String normalizeTemplateKey(String templateKey, String major, String type, String businessLineKey) {
        if (templateKey != null && !templateKey.isBlank()) {
            return templateKey.trim().toLowerCase().replace(' ', '_');
        }

        if ("MOBILE".equals(businessLineKey)) {
            String combined = ((major == null ? "" : major) + " " + (type == null ? "" : type)).toLowerCase();
            if (combined.contains("access")) {
                return "mobile_accessory";
            }
            return "mobile_device";
        }

        return businessLineKey.toLowerCase() + "_default";
    }

    private String normalizeBaseUomCode(String baseUomCode, String businessLineKey) {
        if (baseUomCode != null && !baseUomCode.isBlank()) {
            return baseUomCode.trim().toUpperCase();
        }

        return switch (businessLineKey) {
            case "GOLD" -> "GRAM";
            case "CHEMICAL" -> "LITER";
            default -> "PCS";
        };
    }

    private String normalizePricingPolicyCode(String pricingPolicyCode, String businessLineKey) {
        if (pricingPolicyCode != null && !pricingPolicyCode.isBlank()) {
            return pricingPolicyCode.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        }

        return switch (businessLineKey) {
            case "GOLD" -> "WEIGHT_MARKET";
            case "CHEMICAL" -> "FORMULA";
            default -> "FIXED_RETAIL";
        };
    }

    private Map<String, Object> parseAttributes(String attributesJson) {
        if (attributesJson == null || attributesJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(attributesJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            log.warn("Ignoring invalid product attributes payload during modern inventory save: {}", ex.getMessage());
            return null;
        }
    }

    private void saveAttributeValues(int companyId, long productId, String templateKey, Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM " + TenantSqlIdentifiers.inventoryProductAttributeValueTable(companyId) + " WHERE product_id = :productId",
                new MapSqlParameterSource().addValue("productId", productId)
        );

        if (attributes.isEmpty()) {
            return;
        }

        List<Map<String, Object>> definitions = jdbcTemplate.queryForList(
                """
                SELECT attr.attribute_id, attr.attribute_key, attr.data_type
                FROM %s attr
                JOIN %s template_attr
                  ON template_attr.attribute_id = attr.attribute_id
                JOIN %s template
                  ON template.template_id = template_attr.template_id
                WHERE template.template_key = :templateKey
                """.formatted(
                        TenantSqlIdentifiers.inventoryAttributeDefinitionTable(companyId),
                        TenantSqlIdentifiers.inventoryTemplateAttributeTable(companyId),
                        TenantSqlIdentifiers.inventoryProductTemplateTable(companyId)
                ),
                new MapSqlParameterSource().addValue("templateKey", templateKey)
        );

        Map<String, AttributeDefinition> definitionByKey = new HashMap<>();
        for (Map<String, Object> definition : definitions) {
            String attributeKey = String.valueOf(definition.get("attribute_key"));
            Number attributeId = (Number) definition.get("attribute_id");
            String dataType = String.valueOf(definition.get("data_type"));
            definitionByKey.put(attributeKey, new AttributeDefinition(attributeId.longValue(), dataType));
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            AttributeDefinition definition = definitionByKey.get(entry.getKey());
            if (definition == null) {
                continue;
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("productId", productId)
                    .addValue("attributeId", definition.attributeId())
                    .addValue("valueText", null)
                    .addValue("valueNumber", null)
                    .addValue("valueBoolean", null)
                    .addValue("valueDate", null)
                    .addValue("valueJsonb", null);

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            switch (definition.dataType()) {
                case "BOOLEAN" -> params.addValue("valueBoolean", coerceBoolean(value));
                case "NUMBER" -> params.addValue("valueNumber", coerceNumber(value));
                case "DATE" -> params.addValue("valueDate", value.toString());
                case "JSON" -> params.addValue("valueJsonb", toJson(value));
                default -> params.addValue("valueText", value.toString());
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO %s (
                        product_id, attribute_id, value_text, value_number, value_boolean, value_date, value_jsonb
                    ) VALUES (
                        :productId, :attributeId, :valueText, :valueNumber, :valueBoolean, :valueDate, CAST(:valueJsonb AS jsonb)
                    )
                    """.formatted(TenantSqlIdentifiers.inventoryProductAttributeValueTable(companyId)),
                    params
            );
        }
    }

    private Double coerceNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean coerceBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Could not serialize attribute value to json: {}", ex.getMessage());
            return null;
        }
    }

    private record ProductMetadata(String businessLineKey, String templateKey, String baseUomCode, String pricingPolicyCode) {
    }

    private record AttributeDefinition(long attributeId, String dataType) {
    }
}
