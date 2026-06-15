package com.example.valueinsoftbackend.DatabaseRequests.InventoryAudit;

import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditGroupSummary;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditPageResponse;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditRow;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditSummary;
import com.example.valueinsoftbackend.Model.Request.InventoryAudit.InventoryAuditSearchRequest;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Repository
public class DbInventoryAuditReadModels {

    private static final RowMapper<InventoryAuditRow> AUDIT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public InventoryAuditRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new InventoryAuditRow(
                    rs.getLong("product_id"),
                    getLongOrNull(rs, "product_unit_id"),
                    rs.getString("tracking_type"),
                    rs.getString("serial_identifier"),
                    rs.getString("product_name"),
                    rs.getString("category"),
                    rs.getString("branch"),
                    rs.getInt("opening_qty"),
                    rs.getInt("in_qty"),
                    rs.getInt("out_qty"),
                    rs.getInt("closing_qty"),
                    rs.getInt("unit_price"),
                    rs.getLong("total_value"),
                    rs.getTimestamp("last_movement_date")
            );
        }
    };

    private static final RowMapper<InventoryAuditGroupSummary> GROUP_ROW_MAPPER = (rs, rowNum) ->
            new InventoryAuditGroupSummary(
                    rs.getString("group_key"),
                    rs.getLong("row_count"),
                    rs.getLong("total_closing_qty"),
                    rs.getLong("total_value")
            );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public DbInventoryAuditReadModels(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public InventoryAuditPageResponse search(InventoryAuditSearchRequest request) {
        int page = normalizePage(request.getPage());
        int size = normalizeSize(request.getSize());
        int offset = (page - 1) * size;

        MapSqlParameterSource params = buildParams(request)
                .addValue("limit", size)
                .addValue("offset", offset);

        String baseSql = buildBaseAuditSql(request.getCompanyId());
        String whereClause = buildWhereClause(request, params);

        Long totalItemsValue = namedParameterJdbcTemplate.queryForObject(
                baseSql + " SELECT COUNT(*) FROM audit_base " + whereClause,
                params,
                Long.class
        );
        long totalItems = totalItemsValue == null ? 0L : totalItemsValue;

        ArrayList<InventoryAuditRow> rows = new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql + """
                                 SELECT product_id, product_unit_id, tracking_type, serial_identifier, product_name, category, branch, opening_qty, in_qty, out_qty, closing_qty,
                                        unit_price, total_value, last_movement_date
                                 FROM audit_base
                                 """ + whereClause + buildOrderBy(request) + " LIMIT :limit OFFSET :offset",
                        params,
                        AUDIT_ROW_MAPPER
                )
        );

        InventoryAuditSummary summary = fetchSummary(request);
        ArrayList<InventoryAuditGroupSummary> grouping = fetchGrouping(request);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / (double) size);

        return new InventoryAuditPageResponse(rows, page, size, totalItems, totalPages, summary, grouping);
    }

    public InventoryAuditSummary fetchSummary(InventoryAuditSearchRequest request) {
        MapSqlParameterSource params = buildParams(request);
        String baseSql = buildBaseAuditSql(request.getCompanyId());
        String whereClause = buildWhereClause(request, params);

        return namedParameterJdbcTemplate.queryForObject(
                baseSql + """
                         SELECT
                             COUNT(*) AS total_rows,
                             COALESCE(SUM(opening_qty), 0) AS total_opening_qty,
                             COALESCE(SUM(in_qty), 0) AS total_in_qty,
                             COALESCE(SUM(out_qty), 0) AS total_out_qty,
                             COALESCE(SUM(closing_qty), 0) AS total_closing_qty,
                             COALESCE(SUM(total_value), 0) AS total_stock_value,
                             COALESCE(SUM(CASE
                                 WHEN CAST(:lowStockThreshold AS INTEGER) IS NOT NULL
                                      AND closing_qty <= CAST(:lowStockThreshold AS INTEGER)
                                      AND (:includeOutOfStockInLowStock = TRUE OR closing_qty > 0)
                                      AND (:excludeSerializedFromLowStock = FALSE OR tracking_type NOT IN ('IMEI', 'SERIAL'))
                                 THEN 1
                                 ELSE 0
                             END), 0) AS low_stock_count
                         FROM audit_base
                         """ + whereClause,
                params,
                (rs, rowNum) -> new InventoryAuditSummary(
                        rs.getLong("total_rows"),
                        rs.getLong("total_opening_qty"),
                        rs.getLong("total_in_qty"),
                        rs.getLong("total_out_qty"),
                        rs.getLong("total_closing_qty"),
                        rs.getLong("total_stock_value"),
                        rs.getLong("low_stock_count")
                )
        );
    }

    public ArrayList<InventoryAuditGroupSummary> fetchGrouping(InventoryAuditSearchRequest request) {
        String normalizedGroupBy = normalizeGroupBy(request.getGroupBy());
        if ("NONE".equals(normalizedGroupBy)) {
            return new ArrayList<>();
        }

        MapSqlParameterSource params = buildParams(request);
        String baseSql = buildBaseAuditSql(request.getCompanyId());
        String whereClause = buildWhereClause(request, params);
        String groupingExpression = switch (normalizedGroupBy) {
            case "BRANCH" -> "branch";
            case "CATEGORY" -> "category";
            default -> null;
        };

        if (groupingExpression == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(
                namedParameterJdbcTemplate.query(
                        baseSql + """
                                 SELECT
                                     %s AS group_key,
                                     COUNT(*) AS row_count,
                                     COALESCE(SUM(closing_qty), 0) AS total_closing_qty,
                                     COALESCE(SUM(total_value), 0) AS total_value
                                 FROM audit_base
                                 """.formatted(groupingExpression)
                                + whereClause
                                + """
                                  GROUP BY %s
                                  ORDER BY total_value DESC, group_key ASC
                                  """.formatted(groupingExpression),
                        params,
                        GROUP_ROW_MAPPER
                )
        );
    }

    public void streamRows(InventoryAuditSearchRequest request, Consumer<InventoryAuditRow> rowConsumer) {
        MapSqlParameterSource params = buildParams(request);
        String baseSql = buildBaseAuditSql(request.getCompanyId());
        String whereClause = buildWhereClause(request, params);
        String sql = baseSql + """
                               SELECT product_id, product_unit_id, tracking_type, serial_identifier, product_name, category, branch, opening_qty, in_qty, out_qty, closing_qty,
                                      unit_price, total_value, last_movement_date
                               FROM audit_base
                               """ + whereClause + buildOrderBy(request);

        namedParameterJdbcTemplate.query(sql, params, rs -> {
            int rowNum = 0;
            while (rs.next()) {
                rowConsumer.accept(AUDIT_ROW_MAPPER.mapRow(rs, rowNum++));
            }
            return null;
        });
    }

    private MapSqlParameterSource buildParams(InventoryAuditSearchRequest request) {
        LocalDateTime fromDateTime = request.getFromDate().atStartOfDay();
        LocalDateTime toExclusive = request.getToDate().plusDays(1).atStartOfDay();

        return new MapSqlParameterSource()
                .addValue("branchId", request.getBranchId())
                .addValue("fromTs", Timestamp.valueOf(fromDateTime))
                .addValue("toTs", Timestamp.valueOf(toExclusive))
                .addValue("queryLike", buildLikeValue(request.getQuery()))
                .addValue("productId", request.getProductId())
                .addValue("category", normalizeText(request.getCategory()))
                .addValue("major", normalizeText(request.getMajor()))
                .addValue("businessLineKey", normalizeText(request.getBusinessLineKey()))
                .addValue("templateKey", normalizeText(request.getTemplateKey()))
                .addValue("supplierId", request.getSupplierId())
                .addValue("lowStockOnly", Boolean.TRUE.equals(request.getLowStockOnly()))
                .addValue("lowStockThreshold", request.getLowStockThreshold() == null ? 5 : request.getLowStockThreshold())
                .addValue("excludeSerializedFromLowStock", Boolean.TRUE.equals(request.getExcludeSerializedFromLowStock()))
                .addValue("includeOutOfStockInLowStock", !Boolean.FALSE.equals(request.getIncludeOutOfStockInLowStock()));
    }

    private String buildBaseAuditSql(int companyId) {
        String productTable = TenantSqlIdentifiers.inventoryProductTable(companyId);
        String balanceTable = TenantSqlIdentifiers.inventoryBranchStockBalanceTable(companyId);
        String ledgerTable = TenantSqlIdentifiers.inventoryStockLedgerTable(companyId);
        String unitTable = TenantSqlIdentifiers.inventoryProductUnitTable(companyId);

        return """
               WITH ledger_source AS (
                   SELECT
                       ledger.stock_ledger_id,
                       ledger.branch_id,
                       ledger.product_id,
                       ledger.product_unit_id,
                       ledger.quantity_delta,
                       ledger.movement_type,
                       ledger.created_at
                   FROM %s ledger

                   UNION ALL

                   SELECT
                       -unit.product_unit_id AS stock_ledger_id,
                       unit.branch_id,
                       unit.product_id,
                       unit.product_unit_id,
                       1 AS quantity_delta,
                       'STOCK_IN' AS movement_type,
                       COALESCE(unit.received_at, unit.created_at) AS created_at
                   FROM %s unit
                   JOIN %s unit_product ON unit_product.product_id = unit.product_id
                   WHERE unit.branch_id = :branchId
                     AND COALESCE(unit_product.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                     AND NOT EXISTS (
                         SELECT 1
                         FROM %s existing_ledger
                         WHERE existing_ledger.product_unit_id = unit.product_unit_id
                           AND existing_ledger.quantity_delta > 0
                           AND existing_ledger.movement_type IN ('STOCK_IN', 'OPENING_BALANCE', 'MANUAL_STOCK_IN')
                     )
               ),
               branch_products AS (
                   SELECT DISTINCT balance.product_id, NULL::bigint AS product_unit_id
                   FROM %s balance
                   JOIN %s balance_product ON balance_product.product_id = balance.product_id
                   WHERE balance.branch_id = :branchId
                     AND COALESCE(balance_product.tracking_type, 'QUANTITY') NOT IN ('IMEI', 'SERIAL')
                   UNION
                   SELECT DISTINCT
                       ledger.product_id,
                       CASE
                           WHEN COALESCE(ledger_product.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                           THEN ledger.product_unit_id
                           ELSE NULL::bigint
                       END AS product_unit_id
                   FROM ledger_source ledger
                   JOIN %s ledger_product ON ledger_product.product_id = ledger.product_id
                   WHERE ledger.branch_id = :branchId
               ),
               ledger_rollup AS (
                   SELECT
                       ledger.product_id,
                       CASE
                           WHEN COALESCE(product.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                           THEN ledger.product_unit_id
                           ELSE NULL::bigint
                       END AS product_unit_id,
                       COALESCE(SUM(CASE WHEN ledger.created_at < :fromTs THEN ledger.quantity_delta ELSE 0 END), 0) AS opening_qty,
                       COALESCE(SUM(CASE
                           WHEN ledger.created_at >= :fromTs AND ledger.created_at < :toTs AND ledger.quantity_delta > 0
                           THEN ledger.quantity_delta
                           ELSE 0
                       END), 0) AS in_qty,
                       COALESCE(SUM(CASE
                           WHEN ledger.created_at >= :fromTs AND ledger.created_at < :toTs AND ledger.quantity_delta < 0
                           THEN ABS(ledger.quantity_delta)
                           ELSE 0
                       END), 0) AS out_qty,
                       COALESCE(SUM(CASE WHEN ledger.created_at < :toTs THEN ledger.quantity_delta ELSE 0 END), 0) AS closing_qty,
                       MAX(CASE WHEN ledger.created_at < :toTs THEN ledger.created_at ELSE NULL END) AS last_movement_date
                   FROM ledger_source ledger
                   JOIN %s product ON product.product_id = ledger.product_id
                   WHERE ledger.branch_id = :branchId
                   GROUP BY
                       ledger.product_id,
                       CASE
                           WHEN COALESCE(product.tracking_type, 'QUANTITY') IN ('IMEI', 'SERIAL')
                           THEN ledger.product_unit_id
                           ELSE NULL::bigint
                       END
               ),
               audit_base AS (
                   SELECT
                       product.product_id,
                       branch_products.product_unit_id,
                       COALESCE(product.tracking_type, 'QUANTITY') AS tracking_type,
                       COALESCE(NULLIF(unit.imei, ''), NULLIF(unit.serial_number, ''), '') AS serial_identifier,
                       product.product_name,
                       COALESCE(NULLIF(product.major, ''), NULLIF(product.business_line_key, ''), NULLIF(product.template_key, ''), '') AS category,
                       branch."branchName" AS branch,
                       COALESCE(ledger_rollup.opening_qty, 0) AS opening_qty,
                       COALESCE(ledger_rollup.in_qty, 0) AS in_qty,
                       COALESCE(ledger_rollup.out_qty, 0) AS out_qty,
                       COALESCE(ledger_rollup.closing_qty, 0) AS closing_qty,
                       COALESCE(product.retail_price, 0) AS unit_price,
                       COALESCE(ledger_rollup.closing_qty, 0)::bigint * COALESCE(product.retail_price, 0)::bigint AS total_value,
                       ledger_rollup.last_movement_date,
                       product.major,
                       product.business_line_key,
                       product.template_key,
                       product.supplier_id
                   FROM branch_products branch_products
                   JOIN %s product ON product.product_id = branch_products.product_id
                   JOIN public."Branch" branch ON branch."branchId" = :branchId
                   LEFT JOIN %s unit ON unit.product_unit_id = branch_products.product_unit_id
                   LEFT JOIN ledger_rollup ON ledger_rollup.product_id = product.product_id
                       AND (
                           ledger_rollup.product_unit_id IS NOT DISTINCT FROM branch_products.product_unit_id
                       )
               )
               """.formatted(
                ledgerTable,
                unitTable,
                productTable,
                ledgerTable,
                balanceTable,
                productTable,
                productTable,
                productTable,
                productTable,
                unitTable
        );
    }

    private String buildWhereClause(InventoryAuditSearchRequest request, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (request.getProductId() != null) {
            where.append(" AND product_id = :productId ");
        }

        if (hasText(request.getQuery())) {
            where.append("""
                         AND (
                             CAST(product_id AS TEXT) LIKE :queryLike
                             OR CAST(product_unit_id AS TEXT) LIKE :queryLike
                             OR LOWER(serial_identifier) LIKE :queryLike
                             OR LOWER(tracking_type) LIKE :queryLike
                             OR LOWER(product_name) LIKE :queryLike
                             OR LOWER(category) LIKE :queryLike
                             OR LOWER(branch) LIKE :queryLike
                         )
                         """);
        }

        if (hasText(request.getCategory())) {
            where.append(" AND LOWER(category) = :category ");
        }

        if (hasText(request.getMajor())) {
            where.append(" AND LOWER(major) = :major ");
        }

        if (hasText(request.getBusinessLineKey())) {
            where.append(" AND LOWER(business_line_key) = :businessLineKey ");
        }

        if (hasText(request.getTemplateKey())) {
            where.append(" AND LOWER(template_key) = :templateKey ");
        }

        if (request.getSupplierId() != null) {
            where.append(" AND supplier_id = :supplierId ");
        }

        if (Boolean.TRUE.equals(request.getLowStockOnly())) {
            where.append(" AND closing_qty <= :lowStockThreshold ");
            where.append(" AND (:includeOutOfStockInLowStock = TRUE OR closing_qty > 0) ");
            where.append(" AND (:excludeSerializedFromLowStock = FALSE OR tracking_type NOT IN ('IMEI', 'SERIAL')) ");
        }

        return where.toString();
    }

    private String buildOrderBy(InventoryAuditSearchRequest request) {
        String field = normalizeSortField(request.getSortField());
        String direction = "asc".equalsIgnoreCase(request.getSortDirection()) ? "ASC" : "DESC";
        return " ORDER BY " + field + " " + direction + ", product_id ASC";
    }

    private String normalizeSortField(String sortField) {
        if (sortField == null) {
            return "last_movement_date";
        }

        return switch (sortField.trim().toLowerCase(Locale.ROOT)) {
            case "productid", "product_id" -> "product_id";
            case "productunitid", "product_unit_id" -> "product_unit_id";
            case "serial", "serialidentifier", "serial_identifier", "imei" -> "serial_identifier";
            case "trackingtype", "tracking_type" -> "tracking_type";
            case "productname", "product_name" -> "product_name";
            case "category" -> "category";
            case "openingqty", "opening_qty" -> "opening_qty";
            case "inqty", "in_qty" -> "in_qty";
            case "outqty", "out_qty" -> "out_qty";
            case "closingqty", "closing_qty" -> "closing_qty";
            case "unitprice", "unit_price" -> "unit_price";
            case "totalvalue", "total_value" -> "total_value";
            case "lastmovementdate", "last_movement_date" -> "last_movement_date";
            default -> "last_movement_date";
        };
    }

    private String normalizeGroupBy(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return "NONE";
        }
        return groupBy.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizePage(Integer page) {
        if (page == null || page <= 0) {
            return 1;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return 25;
        }
        return Math.min(size, 100);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeText(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String buildLikeValue(String value) {
        return hasText(value) ? "%" + value.trim().toLowerCase(Locale.ROOT) + "%" : null;
    }

    private static Long getLongOrNull(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
