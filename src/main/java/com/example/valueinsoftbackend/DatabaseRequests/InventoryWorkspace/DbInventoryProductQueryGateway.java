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

import java.util.ArrayList;
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
        item.setBarcode(rs.getString("serial"));
        item.setSerial(rs.getString("serial"));
        item.setBusinessLineKey(rs.getString("businessLineKey"));
        item.setTemplateKey(rs.getString("templateKey"));
        item.setSupplierId(rs.getInt("supplierId"));
        item.setSupplierName(rs.getString("supplierName"));
        item.setQuantityOnHand(rs.getInt("quantity"));
        
        int qty = item.getQuantityOnHand();
        item.setStockStatus(qty > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        item.setLowStock(qty > 0 && qty <= 5); // Simple heuristic
        item.setSellable(true);
        item.setUsed("Used".equalsIgnoreCase(rs.getString("pState")));
        item.setSellPrice(rs.getInt("sellPrice"));
        item.setBuyPrice(rs.getInt("buyPrice"));
        item.setLastMovementAt(null); // Will require ledger join or subquery if needed
        item.setUpdatedAt(rs.getTimestamp("updated_at").toString());
        return item;
    };

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
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String supplierTable = TenantSqlIdentifiers.supplierTable(companyId, branchId);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("p.product_id AS \"productId\", ")
           .append("p.product_name AS \"productName\", ")
           .append("p.serial AS serial, ")
           .append("p.business_line_key AS \"businessLineKey\", ")
           .append("p.template_key AS \"templateKey\", ")
           .append("p.supplier_id AS \"supplierId\", ")
           .append("s.\"SupplierName\" AS \"supplierName\", ")
           .append("COALESCE(st.quantity, 0) AS quantity, ")
           .append("p.product_state AS \"pState\", ")
           .append("p.retail_price AS \"sellPrice\", ")
           .append("p.buying_price AS \"buyPrice\", ")
           .append("p.updated_at ")
           .append("FROM ").append(productTable).append(" p ")
           .append("LEFT JOIN ").append(stockTable).append(" st ON st.product_id = p.product_id AND st.branch_id = :branchId ")
           .append("LEFT JOIN ").append(supplierTable).append(" s ON s.\"supplierId\" = p.supplier_id ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId);

        List<String> conditions = new ArrayList<>();
        
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            conditions.add("(p.product_name ILIKE :query OR p.serial ILIKE :query)");
            params.addValue("query", "%" + request.getQuery().trim() + "%");
        }

        InventoryCatalogFilters filters = request.getFilters();
        if (filters != null) {
            if (filters.getSupplierId() != null) {
                conditions.add("p.supplier_id = :supplierId");
                params.addValue("supplierId", filters.getSupplierId());
            }
            if (filters.getMajor() != null && !filters.getMajor().isBlank()) {
                conditions.add("p.major = :major");
                params.addValue("major", filters.getMajor());
            }
        }

        List<String> chips = request.getChips();
        if (chips != null && !chips.isEmpty()) {
            for (String chip : chips) {
                switch (chip) {
                    case "IN_STOCK":
                        conditions.add("st.quantity > 0");
                        break;
                    case "OUT_OF_STOCK":
                        conditions.add("COALESCE(st.quantity, 0) <= 0");
                        break;
                    case "LOW_STOCK":
                        conditions.add("st.quantity > 0 AND st.quantity <= 5");
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
            else if ("quantityOnHand".equals(request.getSort().getField())) sortField = "st.quantity";
            
            if ("asc".equalsIgnoreCase(request.getSort().getDirection())) sortDir = "ASC";
        }
        sql.append(" ORDER BY ").append(sortField).append(" ").append(sortDir);

        // Pagination
        int offset = (request.getPage() - 1) * request.getPageSize();
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", request.getPageSize());
        params.addValue("offset", offset);

        List<InventoryCatalogItem> data = jdbcTemplate.query(sql.toString(), params, CATALOG_ROW_MAPPER);

        // Total count for pagination
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(productTable).append(" p ");
        countSql.append("LEFT JOIN ").append(stockTable).append(" st ON st.product_id = p.product_id AND st.branch_id = :branchId ");
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
        String sql = getBaseSelect(companyId, branchId) + " WHERE p.serial = :serial";
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
        String stockTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String supplierTable = TenantSqlIdentifiers.supplierTable(companyId, branchId);
        
        return "SELECT p.product_id AS \"productId\", p.product_name AS \"productName\", p.serial AS serial, " +
               "p.business_line_key AS \"businessLineKey\", p.template_key AS \"templateKey\", " +
               "p.supplier_id AS \"supplierId\", s.\"SupplierName\" AS \"supplierName\", " +
               "COALESCE(st.quantity, 0) AS quantity, p.product_state AS \"pState\", " +
               "p.retail_price AS \"sellPrice\", p.buying_price AS \"buyPrice\", p.updated_at " +
               "FROM " + productTable + " p " +
               "LEFT JOIN " + stockTable + " st ON st.product_id = p.product_id AND st.branch_id = :branchId " +
               "LEFT JOIN " + supplierTable + " s ON s.\"supplierId\" = p.supplier_id";
    }
}
