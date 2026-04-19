package com.example.valueinsoftbackend.DatabaseRequests.InventoryWorkspace;

import com.example.valueinsoftbackend.Model.InventoryWorkspace.*;
import com.example.valueinsoftbackend.Model.Request.InventoryWorkspace.InventoryAnalysisRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class DbInventoryAnalysisQueryGateway implements InventoryAnalysisQueryGateway {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbInventoryAnalysisQueryGateway(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<InventoryMovementItem> MOVEMENT_ROW_MAPPER = (rs, rowNum) -> {
        InventoryMovementItem item = new InventoryMovementItem();
        item.setMovementId(rs.getLong("movementId"));
        item.setMovementType(rs.getString("movementType"));
        item.setMovementAt(rs.getTimestamp("movementAt").toString());
        item.setProductId(rs.getLong("productId"));
        item.setProductName(rs.getString("productName"));
        item.setBarcode(rs.getString("barcode"));
        item.setSerial(rs.getString("serial"));
        item.setTemplateKey(rs.getString("templateKey"));
        item.setBusinessLineKey(rs.getString("businessLineKey"));
        item.setSupplierId(rs.getInt("supplierId"));
        item.setSupplierName(rs.getString("supplierName"));
        item.setQuantityDelta(rs.getInt("quantityDelta"));
        item.setRunningBalance(rs.getInt("runningBalance"));
        item.setReferenceType(rs.getString("referenceType"));
        item.setReferenceId(rs.getString("referenceId"));
        item.setActorName(rs.getString("actorName"));
        return item;
    };

    @Override
    public InventoryAnalysisResponse analyzeInventory(String actorName, InventoryAnalysisRequest request) {
        int companyId = request.getCompanyId();
        int branchId = request.getBranchId();
        
        String ledgerTable = TenantSqlIdentifiers.inventoryStockLedgerTable(companyId);
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String supplierTable = TenantSqlIdentifiers.supplierTable(companyId, branchId);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("l.stock_ledger_id AS \"movementId\", ")
           .append("l.movement_type AS \"movementType\", ")
           .append("l.created_at AS \"movementAt\", ")
           .append("l.product_id AS \"productId\", ")
           .append("p.product_name AS \"productName\", ")
           .append("p.serial AS barcode, ")
           .append("p.serial AS serial, ")
           .append("p.template_key AS \"templateKey\", ")
           .append("p.business_line_key AS \"businessLineKey\", ")
           .append("COALESCE(l.supplier_id, p.supplier_id) AS \"supplierId\", ")
           .append("s.\"SupplierName\" AS \"supplierName\", ")
           .append("l.quantity_delta AS \"quantityDelta\", ")
           .append("l.reference_type AS \"referenceType\", ")
           .append("l.reference_id AS \"referenceId\", ")
           .append("l.actor_name AS \"actorName\", ")
           .append("SUM(l.quantity_delta) OVER (PARTITION BY l.product_id ORDER BY l.created_at ASC, l.stock_ledger_id ASC) AS \"runningBalance\" ")
           .append("FROM ").append(ledgerTable).append(" l ")
           .append("JOIN ").append(productTable).append(" p ON p.product_id = l.product_id ")
           .append("LEFT JOIN ").append(supplierTable).append(" s ON s.\"supplierId\" = COALESCE(l.supplier_id, p.supplier_id) ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branchId", branchId);

        List<String> conditions = new ArrayList<>();
        conditions.add("l.branch_id = :branchId");

        if (request.getDateRange() != null) {
            if (request.getDateRange().getFrom() != null && !request.getDateRange().getFrom().isBlank()) {
                conditions.add("l.created_at >= :fromDate::timestamp");
                params.addValue("fromDate", request.getDateRange().getFrom());
            }
            if (request.getDateRange().getTo() != null && !request.getDateRange().getTo().isBlank()) {
                conditions.add("l.created_at <= :toDate::timestamp");
                params.addValue("toDate", request.getDateRange().getTo() + " 23:59:59");
            }
        }

        if (request.getMovementTypes() != null && !request.getMovementTypes().isEmpty()) {
            conditions.add("l.movement_type IN (:movementTypes)");
            params.addValue("movementTypes", request.getMovementTypes());
        }

        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            conditions.add("(p.product_name ILIKE :query OR p.serial ILIKE :query)");
            params.addValue("query", "%" + request.getQuery().trim() + "%");
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        // Sorting
        sql.append(" ORDER BY l.created_at DESC, l.stock_ledger_id DESC");

        // Pagination
        int offset = (request.getPage() - 1) * request.getPageSize();
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", request.getPageSize());
        params.addValue("offset", offset);

        List<InventoryMovementItem> data = jdbcTemplate.query(sql.toString(), params, MOVEMENT_ROW_MAPPER);

        // Total count
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(ledgerTable).append(" l ");
        countSql.append("JOIN ").append(productTable).append(" p ON p.product_id = l.product_id ");
        if (!conditions.isEmpty()) {
            countSql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        Long totalRows = jdbcTemplate.queryForObject(countSql.toString(), params, Long.class);

        InventoryAnalysisResponse response = new InventoryAnalysisResponse();
        response.setMode("analysis");
        response.setQuery(request.getQuery());
        response.setDateRange(request.getDateRange());
        response.setMovementTypes(request.getMovementTypes());
        response.setData(new ArrayList<>(data));
        response.setPagination(new InventoryPagination(
                request.getPage(),
                request.getPageSize(),
                totalRows,
                (int) Math.ceil((double) totalRows / request.getPageSize()),
                offset + data.size() < totalRows
        ));

        // Summary (simplified)
        InventorySummaryResponse summary = new InventorySummaryResponse();
        summary.setMovementCount(totalRows);
        response.setSummary(summary);

        return response;
    }
}
