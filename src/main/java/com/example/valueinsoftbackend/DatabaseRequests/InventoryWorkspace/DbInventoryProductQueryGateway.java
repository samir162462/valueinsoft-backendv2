package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.*;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryCatalogBrowseRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryQuickFindRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

@Repository
@Slf4j
public class DbInventoryProductQueryGateway implements InventoryProductQueryGateway {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbInventoryProductQueryGateway(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<InventoryCatalogItem> CATALOG_ROW_MAPPER = (rs, rowNum) -> {
        InventoryCatalogItem item = new InventoryCatalogItem();
        item.setProductId(rs.getLong("productId"));
        item.setProductName(rs.getString("productName"));
        item.setBarcode(rs.getString("barcode"));
        item.setSerial(rs.getString("serial"));
        item.setTrackingType(rs.getString("trackingType"));
        item.setProductUnitIds(toLongList(rs.getArray("productUnitIds")));
        item.setUnitIdentifiers(toStringList(rs.getArray("unitIdentifiers")));
        item.setUnitSupplierIds(toLongList(rs.getArray("unitSupplierIds")));
        item.setUnitSupplierNames(toStringList(rs.getArray("unitSupplierNames")));
        item.setBusinessLineKey(rs.getString("businessLineKey"));
        item.setTemplateKey(rs.getString("templateKey"));
        item.setGroupKey(rs.getString("groupKey"));
        item.setCategoryKey(rs.getString("categoryKey"));
        item.setSubcategoryKey(rs.getString("subcategoryKey"));
        item.setGroupName(rs.getString("groupName"));
        item.setCategoryName(rs.getString("categoryName"));
        item.setSubcategoryName(rs.getString("subcategoryName"));
        item.setBrand(rs.getString("brand"));
        item.setModel(rs.getString("model"));
        item.setManufacturer(rs.getString("manufacturer"));
        item.setTaxonomyVersion(rs.getInt("taxonomyVersion"));
        item.setSupplierId(rs.getInt("supplierId"));
        item.setSupplierName(rs.getString("supplierName"));
        item.setQuantityOnHand(rs.getInt("quantity"));
        
        int qty = item.getQuantityOnHand();
        item.setStockStatus(qty > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        item.setLowStock(qty > 0 && qty <= 5);
        item.setSellable(true);
        item.setUsed("Used".equalsIgnoreCase(rs.getString("pState")));
        item.setSellPrice(rs.getInt("sellPrice"));
        item.setBuyPrice(rs.getInt("buyPrice"));
        item.setLastMovementAt(null); // Will require ledger join or subquery if needed
        item.setUpdatedAt(rs.getTimestamp("updated_at").toString());
        return item;
    };

    private static List<Long> toLongList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return new ArrayList<>();
        }
        Object value = sqlArray.getArray();
        if (value instanceof Long[] ids) {
            return new ArrayList<>(Arrays.asList(ids));
        }
        if (value instanceof Number[] ids) {
            List<Long> result = new ArrayList<>(ids.length);
            for (Number id : ids) {
                if (id != null) {
                    result.add(id.longValue());
                }
            }
            return result;
        }
        if (value instanceof Object[] ids) {
            List<Long> result = new ArrayList<>(ids.length);
            for (Object id : ids) {
                if (id instanceof Number number) {
                    result.add(number.longValue());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static List<String> toStringList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return new ArrayList<>();
        }
        Object value = sqlArray.getArray();
        if (value instanceof String[] identifiers) {
            return new ArrayList<>(Arrays.asList(identifiers));
        }
        if (value instanceof Object[] identifiers) {
            List<String> result = new ArrayList<>(identifiers.length);
            for (Object identifier : identifiers) {
                if (identifier != null && !identifier.toString().isBlank()) {
                    result.add(identifier.toString());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @Override
    public InventoryQuickFindResponse quickFind(String actorName, InventoryQuickFindRequest request) {
        log.debug("Quick find inventory for company {} branch {} query={}", request.getCompanyId(), request.getBranchId(), request.getQuery());
        
        InventoryQuickFindResponse response = new InventoryQuickFindResponse();
        response.setMode("quickFind");
        response.setQuery(request.getQuery());

        String query = request.getQuery().trim();
        if (query.isEmpty()) {
            response.setEmptyReason("EMPTY_QUERY");
            return response;
        }

        // 1. Try direct ID match if numeric
        if (query.matches("^\\d+$")) {
            InventoryCatalogItem item = findById(request.getCompanyId(), request.getBranchId(), Long.parseLong(query));
            if (item != null) {
                response.setResolvedType("PRODUCT_ID");
                response.setMatchedBy("ID");
                response.setExactMatch(new InventoryExactMatchResult(true, "ID", item));
                return response;
            }
        }

        // 2. Try Serial/Barcode match
        InventoryCatalogItem barcodeItem = findBySerial(request.getCompanyId(), request.getBranchId(), query);
        if (barcodeItem != null) {
            response.setResolvedType("BARCODE");
            response.setMatchedBy("SERIAL");
            response.setExactMatch(new InventoryExactMatchResult(true, "BARCODE", barcodeItem));
            return response;
        }

        // 3. Fallback to fuzzy name search
        List<InventoryCatalogItem> fallbacks = searchByName(request.getCompanyId(), request.getBranchId(), query, 10);
        response.setFallbackMatches(new ArrayList<>(fallbacks));
        response.setResolvedType("FUZZY_NAME");
        
        if (fallbacks.isEmpty()) {
            response.setEmptyReason("NO_MATCHES");
        }

        return response;
    }

    @Override
    public InventoryCatalogBrowseResponse browseCatalog(String actorName, InventoryCatalogBrowseRequest request) {
        int companyId = request.getCompanyId();
        int branchId = request.getBranchId();
        
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String branchProductTable = TenantSqlIdentifiers.inventoryBranchProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String unitTable = TenantSqlIdentifiers.inventoryProductUnitTable(companyId);
        String supplierTable = TenantSqlIdentifiers.supplierTable(companyId, branchId);
        String effectiveQuantitySql = effectiveQuantitySql("p", "st", "serialized_stock");

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("p.product_id AS \"productId\", ")
           .append("p.product_name AS \"productName\", ")
           .append("p.serial AS serial, ")
           .append("p.barcode AS barcode, ")
           .append("COALESCE(p.tracking_type, 'QUANTITY') AS \"trackingType\", ")
           .append("COALESCE(serialized_stock.product_unit_ids, ARRAY[]::bigint[]) AS \"productUnitIds\", ")
           .append("COALESCE(serialized_stock.unit_identifiers, ARRAY[]::text[]) AS \"unitIdentifiers\", ")
           .append("COALESCE(serialized_stock.unit_supplier_ids, ARRAY[]::bigint[]) AS \"unitSupplierIds\", ")
           .append("COALESCE(serialized_stock.unit_supplier_names, ARRAY[]::text[]) AS \"unitSupplierNames\", ")
           .append("p.business_line_key AS \"businessLineKey\", ")
           .append("p.template_key AS \"templateKey\", ")
           .append("ibp.group_key AS \"groupKey\", ")
           .append("ibp.category_key AS \"categoryKey\", ")
           .append("ibp.subcategory_key AS \"subcategoryKey\", ")
           .append("ibp.group_name AS \"groupName\", ")
           .append("COALESCE(ibp.category_name, p.major) AS \"categoryName\", ")
           .append("COALESCE(ibp.subcategory_name, p.product_type) AS \"subcategoryName\", ")
           .append("ibp.brand AS brand, ")
           .append("ibp.model AS model, ")
           .append("ibp.manufacturer AS manufacturer, ")
           .append("COALESCE(ibp.taxonomy_version, 0) AS \"taxonomyVersion\", ")
           .append("ibp.default_supplier_id AS \"supplierId\", ")
           .append("s.\"SupplierName\" AS \"supplierName\", ")
           .append(effectiveQuantitySql).append(" AS quantity, ")
           .append("p.product_state AS \"pState\", ")
           .append("p.retail_price AS \"sellPrice\", ")
           .append("p.buying_price AS \"buyPrice\", ")
           .append("p.updated_at ")
           .append("FROM ").append(productTable).append(" p ")
           .append("INNER JOIN ").append(branchProductTable).append(" ibp ON ibp.product_id = p.product_id AND ibp.branch_id = :branchId AND ibp.is_active = TRUE ")
           .append("LEFT JOIN ").append(stockTable).append(" st ON st.product_id = p.product_id AND st.branch_id = :branchId ")
           .append(serializedStockJoin(unitTable, supplierTable))
           .append("LEFT JOIN ").append(supplierTable).append(" s ON s.\"supplierId\" = ibp.default_supplier_id ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId);

        List<String> conditions = new ArrayList<>();
        
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            conditions.add("(p.product_name ILIKE :query OR p.serial ILIKE :query OR p.barcode ILIKE :query OR EXISTS (" +
                    "SELECT 1 FROM " + unitTable + " unit_search " +
                    "WHERE unit_search.product_id = p.product_id AND unit_search.branch_id = :branchId " +
                    "AND (unit_search.imei ILIKE :query OR unit_search.serial_number ILIKE :query)))");
            params.addValue("query", "%" + request.getQuery().trim() + "%");
        }

        InventoryCatalogFilters filters = request.getFilters();
        int lowStockThreshold = resolveLowStockThreshold(filters);
        boolean excludeSerializedFromLowStock = filters != null && Boolean.TRUE.equals(filters.getExcludeSerializedFromLowStock());
        boolean includeOutOfStockInLowStock = filters == null || !Boolean.FALSE.equals(filters.getIncludeOutOfStockInLowStock());

        params.addValue("lowStockThreshold", lowStockThreshold);

        if (filters != null) {
            if (filters.getSupplierId() != null) {
                conditions.add("ibp.default_supplier_id = :supplierId");
                params.addValue("supplierId", filters.getSupplierId());
            }
            if (hasText(filters.getGroupKey())) {
                conditions.add("ibp.group_key = :groupKey");
                params.addValue("groupKey", filters.getGroupKey().trim());
            }
            if (hasText(filters.getCategoryKey())) {
                conditions.add("ibp.category_key = :categoryKey");
                params.addValue("categoryKey", filters.getCategoryKey().trim());
            }
            if (hasText(filters.getSubcategoryKey())) {
                conditions.add("ibp.subcategory_key = :subcategoryKey");
                params.addValue("subcategoryKey", filters.getSubcategoryKey().trim());
            }
            if (filters.getMajor() != null && !filters.getMajor().isBlank()) {
                conditions.add("(LOWER(COALESCE(ibp.group_name, '')) = LOWER(:major) OR LOWER(COALESCE(ibp.category_name, p.major, '')) = LOWER(:major) OR LOWER(COALESCE(ibp.subcategory_name, p.product_type, '')) = LOWER(:major) OR ibp.group_key = :major OR ibp.category_key = :major OR ibp.subcategory_key = :major OR p.major = :major)");
                params.addValue("major", filters.getMajor().trim());
            }
        }

        List<String> chips = request.getChips();
        if (chips != null && !chips.isEmpty()) {
            for (String chip : chips) {
                switch (chip) {
                    case "IN_STOCK":
                        conditions.add(effectiveQuantitySql + " > 0");
                        break;
                    case "OUT_OF_STOCK":
                        conditions.add(effectiveQuantitySql + " <= 0");
                        break;
                    case "LOW_STOCK":
                        conditions.add(buildLowStockCondition(
                                effectiveQuantitySql,
                                "p",
                                includeOutOfStockInLowStock,
                                excludeSerializedFromLowStock
                        ));
                        break;
                    case "USED":
                        conditions.add("p.product_state = 'Used'");
                        break;
                    case "SELLABLE":
                        // Active/Sellable items (not deleted or hidden)
                        conditions.add("p.product_state NOT IN ('DELETED', 'HIDDEN')");
                        break;
                    case "RECENTLY_ADDED":
                        conditions.add("p.updated_at >= (NOW() - INTERVAL '7 days')");
                        break;
                }
            }
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        // Sorting
        String sortField = "p.updated_at";
        String sortDir = "DESC";
        if (request.getSort() != null) {
            if ("productName".equals(request.getSort().getField())) sortField = "p.product_name";
            else if ("quantityOnHand".equals(request.getSort().getField())) sortField = effectiveQuantitySql;
            
            if ("asc".equalsIgnoreCase(request.getSort().getDirection())) sortDir = "ASC";
        }
        sql.append(" ORDER BY ").append(sortField).append(" ").append(sortDir);

        // Pagination
        int offset = (request.getPage() - 1) * request.getPageSize();
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", request.getPageSize());
        params.addValue("offset", offset);

        List<InventoryCatalogItem> data = jdbcTemplate.query(sql.toString(), params, CATALOG_ROW_MAPPER);
        data.forEach((item) -> item.setLowStock(isLowStockItem(
                item,
                lowStockThreshold,
                includeOutOfStockInLowStock,
                excludeSerializedFromLowStock
        )));

        // Total count for pagination
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(productTable).append(" p ");
        countSql.append("INNER JOIN ").append(branchProductTable).append(" ibp ON ibp.product_id = p.product_id AND ibp.branch_id = :branchId AND ibp.is_active = TRUE ");
        countSql.append("LEFT JOIN ").append(stockTable).append(" st ON st.product_id = p.product_id AND st.branch_id = :branchId ");
        countSql.append(serializedStockJoin(unitTable, supplierTable));
        if (!conditions.isEmpty()) {
            countSql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        Long totalRows = jdbcTemplate.queryForObject(countSql.toString(), params, Long.class);

        InventoryCatalogBrowseResponse response = new InventoryCatalogBrowseResponse();
        response.setMode("browse");
        response.setData(new ArrayList<>(data));
        response.setPagination(new InventoryPagination(
                request.getPage(),
                request.getPageSize(),
                totalRows,
                (int) Math.ceil((double) totalRows / request.getPageSize()),
                offset + data.size() < totalRows
        ));
        
        // Summary stats (simplified)
        InventorySummaryResponse summary = new InventorySummaryResponse();
        summary.setResultCount(totalRows);
        response.setSummary(summary);

        return response;
    }

    private InventoryCatalogItem findById(int companyId, int branchId, long productId) {
        String sql = getBaseSelect(companyId, branchId) + " WHERE p.product_id = :productId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("productId", productId);
        List<InventoryCatalogItem> results = jdbcTemplate.query(sql, params, CATALOG_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    private InventoryCatalogItem findBySerial(int companyId, int branchId, String serial) {
        String unitTable = TenantSqlIdentifiers.inventoryProductUnitTable(companyId);
        String sql = getBaseSelect(companyId, branchId) + " WHERE p.serial = :serial OR p.barcode = :serial OR EXISTS (" +
                "SELECT 1 FROM " + unitTable + " unit_search " +
                "WHERE unit_search.product_id = p.product_id AND unit_search.branch_id = :branchId " +
                "AND (unit_search.imei = :serial OR unit_search.serial_number = :serial))";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("serial", serial);
        List<InventoryCatalogItem> results = jdbcTemplate.query(sql, params, CATALOG_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    private List<InventoryCatalogItem> searchByName(int companyId, int branchId, String name, int limit) {
        String sql = getBaseSelect(companyId, branchId) + " WHERE p.product_name ILIKE :name LIMIT :limit";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId)
                .addValue("name", "%" + name + "%")
                .addValue("limit", limit);
        return jdbcTemplate.query(sql, params, CATALOG_ROW_MAPPER);
    }

    private String getBaseSelect(int companyId, int branchId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String branchProductTable = TenantSqlIdentifiers.inventoryBranchProductTable(companyId);
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String unitTable = TenantSqlIdentifiers.inventoryProductUnitTable(companyId);
        String supplierTable = TenantSqlIdentifiers.supplierTable(companyId, branchId);
        String effectiveQuantitySql = effectiveQuantitySql("p", "st", "serialized_stock");
        
        return "SELECT p.product_id AS \"productId\", p.product_name AS \"productName\", p.serial AS serial, " +
               "p.barcode AS barcode, COALESCE(p.tracking_type, 'QUANTITY') AS \"trackingType\", " +
               "COALESCE(serialized_stock.product_unit_ids, ARRAY[]::bigint[]) AS \"productUnitIds\", " +
               "COALESCE(serialized_stock.unit_identifiers, ARRAY[]::text[]) AS \"unitIdentifiers\", " +
               "COALESCE(serialized_stock.unit_supplier_ids, ARRAY[]::bigint[]) AS \"unitSupplierIds\", " +
               "COALESCE(serialized_stock.unit_supplier_names, ARRAY[]::text[]) AS \"unitSupplierNames\", " +
               "p.business_line_key AS \"businessLineKey\", p.template_key AS \"templateKey\", " +
               "ibp.group_key AS \"groupKey\", ibp.category_key AS \"categoryKey\", ibp.subcategory_key AS \"subcategoryKey\", " +
               "ibp.group_name AS \"groupName\", COALESCE(ibp.category_name, p.major) AS \"categoryName\", " +
               "COALESCE(ibp.subcategory_name, p.product_type) AS \"subcategoryName\", ibp.brand AS brand, " +
               "ibp.model AS model, ibp.manufacturer AS manufacturer, COALESCE(ibp.taxonomy_version, 0) AS \"taxonomyVersion\", " +
               "ibp.default_supplier_id AS \"supplierId\", s.\"SupplierName\" AS \"supplierName\", " +
               effectiveQuantitySql + " AS quantity, p.product_state AS \"pState\", " +
               "p.retail_price AS \"sellPrice\", p.buying_price AS \"buyPrice\", p.updated_at " +
               "FROM " + productTable + " p " +
               "INNER JOIN " + branchProductTable + " ibp ON ibp.product_id = p.product_id AND ibp.branch_id = :branchId AND ibp.is_active = TRUE " +
               "LEFT JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = :branchId " +
               serializedStockJoin(unitTable, supplierTable) +
               "LEFT JOIN " + supplierTable + " s ON s.\"supplierId\" = ibp.default_supplier_id";
    }

    private static String effectiveQuantitySql(String productAlias, String stockAlias, String serializedAlias) {
        return "CASE WHEN COALESCE(" + productAlias + ".tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL') " +
                "THEN COALESCE(" + serializedAlias + ".available_quantity, 0) " +
                "ELSE COALESCE(" + stockAlias + ".quantity, 0) END";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static int resolveLowStockThreshold(InventoryCatalogFilters filters) {
        if (filters == null || filters.getLowStockThreshold() == null || filters.getLowStockThreshold() < 0) {
            return 5;
        }
        return filters.getLowStockThreshold();
    }

    private static String buildLowStockCondition(String effectiveQuantitySql,
                                                 String productAlias,
                                                 boolean includeOutOfStockInLowStock,
                                                 boolean excludeSerializedFromLowStock) {
        List<String> conditions = new ArrayList<>();
        conditions.add(effectiveQuantitySql + " <= :lowStockThreshold");
        if (!includeOutOfStockInLowStock) {
            conditions.add(effectiveQuantitySql + " > 0");
        }
        if (excludeSerializedFromLowStock) {
            conditions.add("COALESCE(" + productAlias + ".tracking_type, 'QUANTITY') NOT IN ('IMEI', 'SERIAL')");
        }
        return "(" + String.join(" AND ", conditions) + ")";
    }

    private static boolean isLowStockItem(InventoryCatalogItem item,
                                          int lowStockThreshold,
                                          boolean includeOutOfStockInLowStock,
                                          boolean excludeSerializedFromLowStock) {
        if (item == null) {
            return false;
        }
        String trackingType = item.getTrackingType() == null ? "QUANTITY" : item.getTrackingType().trim().toUpperCase();
        if (excludeSerializedFromLowStock && ("IMEI".equals(trackingType) || "SERIAL".equals(trackingType))) {
            return false;
        }
        int quantity = item.getQuantityOnHand();
        if (!includeOutOfStockInLowStock && quantity <= 0) {
            return false;
        }
        return quantity <= lowStockThreshold;
    }

    private static String serializedStockJoin(String unitTable, String supplierTable) {
        return "LEFT JOIN ( " +
                "SELECT product_id, " +
                "COUNT(*) FILTER (WHERE status = 'AVAILABLE') AS available_quantity, " +
                "ARRAY_AGG(product_unit_id ORDER BY product_unit_id) FILTER (WHERE status = 'AVAILABLE') AS product_unit_ids, " +
                "ARRAY_AGG(COALESCE(NULLIF(imei, ''), NULLIF(serial_number, '')) ORDER BY product_unit_id) " +
                "FILTER (WHERE status = 'AVAILABLE' AND COALESCE(NULLIF(imei, ''), NULLIF(serial_number, '')) IS NOT NULL) AS unit_identifiers, " +
                "ARRAY_AGG(unit.supplier_id::bigint ORDER BY product_unit_id) FILTER (WHERE status = 'AVAILABLE' AND unit.supplier_id IS NOT NULL) AS unit_supplier_ids, " +
                "ARRAY_AGG(unit_supplier.\"SupplierName\" ORDER BY product_unit_id) FILTER (WHERE status = 'AVAILABLE' AND unit_supplier.\"SupplierName\" IS NOT NULL) AS unit_supplier_names " +
                "FROM " + unitTable + " unit " +
                "LEFT JOIN " + supplierTable + " unit_supplier ON unit_supplier.\"supplierId\" = unit.supplier_id " +
                "WHERE unit.branch_id = :branchId " +
                "GROUP BY unit.product_id " +
                ") serialized_stock ON serialized_stock.product_id = p.product_id ";
    }
}
