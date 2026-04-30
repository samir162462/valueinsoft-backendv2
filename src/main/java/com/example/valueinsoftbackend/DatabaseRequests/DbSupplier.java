/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Response.SupplierAgingBucketResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAgingResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAuditEventResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierAuditResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierOpenDocumentResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierReferenceResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierStatementLineResponse;
import com.example.valueinsoftbackend.Model.Response.SupplierStatementResponse;
import com.example.valueinsoftbackend.Model.Supplier;
import com.example.valueinsoftbackend.Model.SupplierBProduct;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
@Slf4j
public class DbSupplier {

    private static final RowMapper<Supplier> SUPPLIER_ROW_MAPPER = (rs, rowNum) -> new Supplier(
            rs.getInt("supplierId"),
            rs.getString("SupplierName"),
            rs.getString("supplierPhone1"),
            rs.getString("supplierPhone2"),
            rs.getString("SupplierLocation"),
            rs.getString("suplierMajor"),
            rs.getInt("supplierTotalSales"),
            rs.getInt("supplierRemainig"),
            rs.getString("supplierStatus"),
            rs.getString("archivedAt"),
            nullableInteger(rs, "archivedBy"),
            rs.getString("archiveReason")
    );

    private static final RowMapper<InventoryTransaction> INVENTORY_TRANSACTION_ROW_MAPPER = (rs, rowNum) -> new InventoryTransaction(
            rs.getInt("transId"),
            rs.getInt("productId"),
            rs.getString("productName"),
            rs.getString("serial"),
            rs.getString("userName"),
            rs.getInt("supplierId"),
            rs.getString("transactionType"),
            rs.getInt("NumItems"),
            rs.getInt("transTotal"),
            rs.getString("payType"),
            rs.getTimestamp("time"),
            rs.getInt("RemainingAmount"),
            rs.getInt("runningBalance")
    );

    private static final RowMapper<SupplierBProduct> SUPPLIER_B_PRODUCT_ROW_MAPPER = (rs, rowNum) -> new SupplierBProduct(
            rs.getInt("sBPId"),
            rs.getInt("productId"),
            rs.getInt("supplierId"),
            rs.getInt("quantity"),
            rs.getInt("cost"),
            rs.getString("userName"),
            rs.getInt("sPaid"),
            rs.getTimestamp("time"),
            rs.getString("desc"),
            rs.getInt("orderDetailsId"),
            rs.getString("postingStatus"),
            nullableUuid(rs, "postingRequestId"),
            nullableUuid(rs, "journalId"),
            rs.getString("postingFailureReason")
    );

    private final JdbcTemplate jdbcTemplate;

    public DbSupplier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Supplier> getSuppliers(int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "SELECT \"supplierId\", \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", " +
                "\"SupplierLocation\", \"suplierMajor\", \"supplierTotalSales\", \"supplierRemainig\", " +
                "\"supplierStatus\", \"archivedAt\"::text AS \"archivedAt\", \"archivedBy\", \"archiveReason\" " +
                "FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId);
        return jdbcTemplate.query(sql, SUPPLIER_ROW_MAPPER);
    }

    public int addSupplier(String name, String phone1, String phone2, String location, String major, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "INSERT INTO " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " (\"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", \"SupplierLocation\", \"suplierMajor\", " +
                "\"normalizedSupplierName\", \"normalizedSupplierPhone1\") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(
                sql,
                name,
                phone1,
                phone2,
                location,
                major,
                normalizeSupplierNameKey(name),
                normalizeSupplierPhoneKey(phone1)
        );
    }

    public int updateSupplier(int supplierId, String name, String phone1, String phone2, String location, String major, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"SupplierName\" = ?, \"supplierPhone1\" = ?, \"supplierPhone2\" = ?, \"SupplierLocation\" = ?, \"suplierMajor\" = ?, " +
                "\"normalizedSupplierName\" = ?, \"normalizedSupplierPhone1\" = ? " +
                "WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(
                sql,
                name,
                phone1,
                phone2,
                location,
                major,
                normalizeSupplierNameKey(name),
                normalizeSupplierPhoneKey(phone1),
                supplierId
        );
    }

    public int deleteSupplier(int supplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "DELETE FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE \"supplierId\" = ?";
        return jdbcTemplate.update(sql, supplierId);
    }

    public boolean supplierExists(int supplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE \"supplierId\" = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, supplierId);
        return count != null && count > 0;
    }

    public boolean supplierNameExists(String normalizedName, int excludedSupplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE COALESCE(\"normalizedSupplierName\", LOWER(REGEXP_REPLACE(TRIM(COALESCE(\"SupplierName\", '')), '\\s+', ' ', 'g'))) = ? " +
                "AND (? <= 0 OR \"supplierId\" <> ?)";
        return count(sql, normalizedName, excludedSupplierId, excludedSupplierId) > 0;
    }

    public boolean supplierPrimaryPhoneExists(String normalizedPhone, int excludedSupplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE COALESCE(\"normalizedSupplierPhone1\", REGEXP_REPLACE(COALESCE(\"supplierPhone1\", ''), '[^0-9+]', '', 'g')) = ? " +
                "AND (? <= 0 OR \"supplierId\" <> ?)";
        return count(sql, normalizedPhone, excludedSupplierId, excludedSupplierId) > 0;
    }

    public int archiveSupplier(int supplierId, int branchId, int companyId, String reason, Integer archivedBy) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"supplierStatus\" = 'archived', \"archivedAt\" = NOW(), \"archivedBy\" = ?, \"archiveReason\" = ? " +
                "WHERE \"supplierId\" = ? AND COALESCE(\"supplierStatus\", 'active') <> 'archived'";
        return jdbcTemplate.update(sql, archivedBy, reason, supplierId);
    }

    public int reactivateSupplier(int supplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "UPDATE " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " SET \"supplierStatus\" = 'active' " +
                "WHERE \"supplierId\" = ? AND COALESCE(\"supplierStatus\", 'active') = 'archived'";
        return jdbcTemplate.update(sql, supplierId);
    }

    public SupplierReferenceResponse getSupplierReferences(int supplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        Map<String, Long> references = new LinkedHashMap<>();
        references.put("inventoryItems", countInventoryItems(companyId, supplierId));
        references.put("inventoryTransactions", countInventoryTransactions(companyId, branchId, supplierId));
        references.put("supplierReceipts", countSupplierReceipts(companyId, branchId, supplierId));
        references.put("supplierReturns", countSupplierReturns(companyId, branchId, supplierId));
        references.put("purchaseDocuments", 0L);
        references.put("financePostingRequests", countFinancePostingRequests(companyId, branchId, supplierId));
        references.put("journalEntries", countJournalEntries(companyId, branchId, supplierId));
        references.put("openDocuments", countOpenDocuments(companyId, branchId, supplierId));

        BigDecimal openBalance = getSupplierOpenBalance(companyId, branchId, supplierId);
        boolean hasReferences = references.values().stream().anyMatch(value -> value != null && value > 0);
        boolean hasOpenBalance = openBalance.compareTo(BigDecimal.ZERO) != 0;
        return new SupplierReferenceResponse(
                supplierId,
                !hasReferences && !hasOpenBalance,
                true,
                references,
                openBalance
        );
    }

    public SupplierStatementResponse getSupplierStatement(int supplierId,
                                                          int branchId,
                                                          int companyId,
                                                          LocalDate fromDate,
                                                          LocalDate toDate) {
        Supplier supplier = getSupplierById(supplierId, branchId, companyId);
        List<SupplierStatementLineResponse> openingLines = getSupplierStatementLines(
                supplierId,
                branchId,
                companyId,
                null,
                Timestamp.valueOf(fromDate.atStartOfDay())
        );
        List<SupplierStatementLineResponse> periodLines = getSupplierStatementLines(
                supplierId,
                branchId,
                companyId,
                Timestamp.valueOf(fromDate.atStartOfDay()),
                Timestamp.valueOf(toDate.plusDays(1).atStartOfDay())
        );

        BigDecimal openingBalance = sumCreditMinusDebit(openingLines);
        BigDecimal debits = sumDebit(periodLines);
        BigDecimal credits = sumCredit(periodLines);
        BigDecimal runningBalance = openingBalance;
        for (SupplierStatementLineResponse line : periodLines) {
            runningBalance = runningBalance.add(line.getCredit()).subtract(line.getDebit());
            line.setBalance(runningBalance);
        }

        return new SupplierStatementResponse(
                supplierId,
                supplier.getSupplierName(),
                "EGP",
                openingBalance,
                debits,
                credits,
                runningBalance,
                periodLines
        );
    }

    public SupplierAgingResponse getSupplierAging(int supplierId,
                                                  int branchId,
                                                  int companyId,
                                                  LocalDate asOfDate) {
        Supplier supplier = getSupplierById(supplierId, branchId, companyId);
        List<SupplierOpenDocumentResponse> documents = getOpenSupplierDocuments(
                supplierId,
                branchId,
                companyId,
                asOfDate
        );

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days1To30 = BigDecimal.ZERO;
        BigDecimal days31To60 = BigDecimal.ZERO;
        BigDecimal days61To90 = BigDecimal.ZERO;
        BigDecimal over90 = BigDecimal.ZERO;

        for (SupplierOpenDocumentResponse document : documents) {
            switch (document.getBucket()) {
                case "current" -> current = current.add(document.getOpenAmount());
                case "days1To30" -> days1To30 = days1To30.add(document.getOpenAmount());
                case "days31To60" -> days31To60 = days31To60.add(document.getOpenAmount());
                case "days61To90" -> days61To90 = days61To90.add(document.getOpenAmount());
                default -> over90 = over90.add(document.getOpenAmount());
            }
        }

        BigDecimal total = current.add(days1To30).add(days31To60).add(days61To90).add(over90);
        return new SupplierAgingResponse(
                supplierId,
                supplier.getSupplierName(),
                "EGP",
                asOfDate.toString(),
                new SupplierAgingBucketResponse(current, days1To30, days31To60, days61To90, over90, total),
                documents
        );
    }

    public SupplierAuditResponse getSupplierAudit(int supplierId,
                                                  int branchId,
                                                  int companyId,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  int page,
                                                  int size) {
        getSupplierById(supplierId, branchId, companyId);
        String sql = """
                SELECT e.created_at AS event_time,
                       e.event_type,
                       COALESCE(u."userName", 'system') AS actor,
                       e.entity_type AS source_type,
                       e.entity_id AS source_id,
                       COALESCE(e.reason, e.event_type) AS summary,
                       COALESCE(e.after_state::text, e.before_state::text, '{}') AS changes
                FROM public.finance_audit_event e
                LEFT JOIN %s u ON u."userId" = e.actor_user_id
                WHERE e.company_id = ?
                  AND (e.branch_id = ? OR e.branch_id IS NULL)
                  AND e.created_at >= ?
                  AND e.created_at < ?
                  AND (
                      e.after_state ->> 'supplierId' = ?
                      OR e.before_state ->> 'supplierId' = ?
                      OR e.entity_id = ?
                  )
                ORDER BY e.created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(TenantSqlIdentifiers.userTable(companyId));

        List<SupplierAuditEventResponse> events = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SupplierAuditEventResponse(
                        rs.getTimestamp("event_time").toInstant(),
                        rs.getString("event_type"),
                        rs.getString("actor"),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getString("summary"),
                        rs.getString("changes")
                ),
                companyId,
                branchId,
                Timestamp.valueOf(fromDate.atStartOfDay()),
                Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()),
                String.valueOf(supplierId),
                String.valueOf(supplierId),
                String.valueOf(supplierId),
                size,
                page * size
        );

        return new SupplierAuditResponse(supplierId, page, size, events);
    }

    public JsonObject getRemainingSupplierAmountByProductId(int productId, int branchId, int companyId) {
        String sql = "SELECT ledger.product_id AS \"productId\", ledger.created_at AS \"time\", " +
                "COALESCE(ledger.pay_type, '') AS \"payType\", COALESCE(ledger.remaining_amount, 0) AS \"remainingAmount\" " +
                "FROM " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) + " ledger " +
                "WHERE ledger.branch_id = ? AND ledger.product_id = ? " +
                "ORDER BY ledger.created_at DESC, ledger.stock_ledger_id DESC LIMIT 1";

        ResultSetExtractor<JsonObject> extractor = rs -> {
            JsonObject json = new JsonObject();
            if (rs.next()) {
                json.addProperty("productId", rs.getInt("productId"));
                json.addProperty("time", rs.getString("time"));
                json.addProperty("payType", rs.getString("payType"));
                json.addProperty("remainingAmount", rs.getInt("remainingAmount"));
            }
            return json;
        };

        return jdbcTemplate.query(sql, extractor, branchId, productId);
    }

    public List<InventoryTransaction> getSupplierSales(int branchId, int supplierId, int companyId) {
        String sql = """
                SELECT
                    ledger.stock_ledger_id AS "transId",
                    ledger.product_id AS "productId",
                    prod.product_name AS "productName",
                    prod.serial AS "serial",
                    COALESCE(ledger.actor_name, 'system') AS "userName",
                    COALESCE(ledger.supplier_id, 0) AS "supplierId",
                    CASE ledger.movement_type
                        WHEN 'SALE_OUT' THEN 'Sold'
                        WHEN 'BOUNCE_BACK_IN' THEN 'BounceBackInv'
                        WHEN 'OPENING_BALANCE' THEN 'Add'
                        WHEN 'MANUAL_STOCK_IN' THEN 'Add'
                        WHEN 'MANUAL_STOCK_OUT' THEN 'Update'
                        WHEN 'DAMAGED_OUT' THEN 'Damaged'
                        ELSE ledger.movement_type
                    END AS "transactionType",
                    ledger.quantity_delta AS "NumItems",
                    COALESCE(ledger.trans_total, 0) AS "transTotal",
                    COALESCE(ledger.pay_type, '') AS "payType",
                    ledger.created_at AS "time",
                    COALESCE(ledger.remaining_amount, 0) AS "RemainingAmount",
                    SUM(ledger.quantity_delta) OVER (
                        PARTITION BY ledger.branch_id, ledger.product_id
                        ORDER BY ledger.created_at ASC, ledger.stock_ledger_id ASC
                    ) AS "runningBalance"
                FROM %s ledger
                JOIN %s prod ON prod.product_id = ledger.product_id
                WHERE ledger.branch_id = ? AND COALESCE(ledger.supplier_id, 0) = ?
                ORDER BY ledger.created_at DESC, ledger.stock_ledger_id DESC
                """.formatted(
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.inventoryProductTable(companyId)
        );
        return jdbcTemplate.query(sql, INVENTORY_TRANSACTION_ROW_MAPPER, branchId, supplierId);
    }

    public List<SupplierBProduct> getSupplierBProduct(int branchId, int supplierId, int companyId) {
        String sql = "SELECT returned.\"sBPId\", returned.\"productId\", returned.\"supplierId\", returned.quantity, returned.cost, returned.\"userName\", returned.\"sPaid\", " +
                "returned.\"time\", returned.\"desc\", returned.\"orderDetailsId\", " +
                "fp.status AS \"postingStatus\", fp.posting_request_id AS \"postingRequestId\", " +
                "fp.journal_entry_id AS \"journalId\", fp.last_error AS \"postingFailureReason\" " +
                "FROM " + TenantSqlIdentifiers.supplierBoughtProductTable(companyId) + " returned " +
                "LEFT JOIN public.finance_posting_request fp " +
                "  ON fp.company_id = ? " +
                " AND fp.branch_id = returned.\"branchId\" " +
                " AND fp.source_module = 'purchase' " +
                " AND fp.source_type = 'supplier_return' " +
                " AND fp.source_id = 'supplier-return-' || returned.\"sBPId\"::text " +
                "WHERE returned.\"branchId\" = ? AND returned.\"supplierId\" = ? " +
                "ORDER BY returned.\"time\" DESC, returned.\"sBPId\" DESC";
        return jdbcTemplate.query(sql, SUPPLIER_B_PRODUCT_ROW_MAPPER, companyId, branchId, supplierId);
    }

    public SupplierBProduct addSupplierBProduct(SupplierBProduct supplierBProduct, int productId, int branchId, int companyId) {
        String sql = "INSERT INTO " + TenantSqlIdentifiers.supplierBoughtProductTable(companyId) +
                " (\"productId\", quantity, cost, \"userName\", \"sPaid\", \"time\", \"desc\", \"orderDetailsId\", \"supplierId\", \"branchId\") " +
                "SELECT ?, ?, ?, ?, ?, ?, ?, ?, p.supplier_id, ? " +
                "FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) + " p " +
                "WHERE p.product_id = ? " +
                "RETURNING \"sBPId\", \"productId\", \"supplierId\", quantity, cost, \"userName\", \"sPaid\", \"time\", \"desc\", \"orderDetailsId\", " +
                "NULL::varchar AS \"postingStatus\", NULL::uuid AS \"postingRequestId\", NULL::uuid AS \"journalId\", NULL::text AS \"postingFailureReason\"";

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    SUPPLIER_B_PRODUCT_ROW_MAPPER,
                    productId,
                    supplierBProduct.getQuantity(),
                    supplierBProduct.getCost(),
                    supplierBProduct.getUserName(),
                    supplierBProduct.getsPaid(),
                    new Timestamp(supplierBProduct.getTime().getTime()),
                    supplierBProduct.getDesc(),
                    supplierBProduct.getOrderDetailsId(),
                    branchId,
                    productId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void ensureSupplierLifecycleColumns(int companyId, int branchId) {
        String table = TenantSqlIdentifiers.supplierTable(companyId, branchId);
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"supplierStatus\" VARCHAR(32) NOT NULL DEFAULT 'active'");
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"archivedAt\" TIMESTAMP NULL");
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"archivedBy\" INTEGER NULL");
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"archiveReason\" VARCHAR(500) NULL");
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"normalizedSupplierName\" VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS \"normalizedSupplierPhone1\" VARCHAR(64)");
        backfillSupplierNormalizedKeys(table);
        ensureSupplierNormalizedIndexes(table, companyId, branchId);
    }

    private void backfillSupplierNormalizedKeys(String table) {
        jdbcTemplate.update(
                "UPDATE " + table + " SET " +
                        "\"normalizedSupplierName\" = LOWER(REGEXP_REPLACE(TRIM(COALESCE(\"SupplierName\", '')), '\\s+', ' ', 'g')), " +
                        "\"normalizedSupplierPhone1\" = REGEXP_REPLACE(COALESCE(\"supplierPhone1\", ''), '[^0-9+]', '', 'g') " +
                        "WHERE \"normalizedSupplierName\" IS NULL OR \"normalizedSupplierPhone1\" IS NULL"
        );
    }

    private void ensureSupplierNormalizedIndexes(String table, int companyId, int branchId) {
        String nameLookupIndex = "idx_supplier_" + companyId + "_" + branchId + "_normalized_name_lookup";
        String phoneLookupIndex = "idx_supplier_" + companyId + "_" + branchId + "_normalized_phone1_lookup";
        String nameUniqueIndex = "ux_supplier_" + companyId + "_" + branchId + "_normalized_name";
        String phoneUniqueIndex = "ux_supplier_" + companyId + "_" + branchId + "_normalized_phone1";
        if (hasDuplicateNormalizedValues(table, "\"normalizedSupplierName\"")) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + nameLookupIndex + " ON " + table + " (\"normalizedSupplierName\")");
            log.warn("Supplier table {} has duplicate normalized names; created non-unique index until cleanup is complete", table);
        } else {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + nameUniqueIndex + " ON " + table + " (\"normalizedSupplierName\") WHERE \"normalizedSupplierName\" <> ''");
        }

        if (hasDuplicateNormalizedValues(table, "\"normalizedSupplierPhone1\"")) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + phoneLookupIndex + " ON " + table + " (\"normalizedSupplierPhone1\")");
            log.warn("Supplier table {} has duplicate normalized primary phones; created non-unique index until cleanup is complete", table);
        } else {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + phoneUniqueIndex + " ON " + table + " (\"normalizedSupplierPhone1\") WHERE \"normalizedSupplierPhone1\" <> ''");
        }
    }

    private boolean hasDuplicateNormalizedValues(String table, String columnName) {
        String sql = "SELECT COUNT(*) FROM (" +
                "SELECT " + columnName + " FROM " + table + " " +
                "WHERE COALESCE(" + columnName + ", '') <> '' " +
                "GROUP BY " + columnName + " HAVING COUNT(*) > 1 LIMIT 1" +
                ") duplicates";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null && count > 0;
    }

    private long countInventoryItems(int companyId, int supplierId) {
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.inventoryProductTable(companyId) +
                " WHERE supplier_id = ?";
        return count(sql, supplierId);
    }

    private long countInventoryTransactions(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) +
                " WHERE branch_id = ? AND supplier_id = ?";
        return count(sql, branchId, supplierId);
    }

    private long countSupplierReceipts(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.supplierReceiptsTable(companyId) +
                " WHERE \"branchId\" = ? AND \"supplierId\" = ?";
        return count(sql, branchId, supplierId);
    }

    private long countSupplierReturns(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COUNT(*) FROM " + TenantSqlIdentifiers.supplierBoughtProductTable(companyId) +
                " WHERE \"branchId\" = ? AND \"supplierId\" = ?";
        return count(sql, branchId, supplierId);
    }

    private long countFinancePostingRequests(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COUNT(*) FROM public.finance_posting_request " +
                "WHERE company_id = ? AND (branch_id = ? OR branch_id IS NULL) " +
                "AND request_payload ->> 'supplierId' = ?";
        return count(sql, companyId, branchId, String.valueOf(supplierId));
    }

    private long countJournalEntries(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COUNT(DISTINCT journal_entry_id) FROM public.finance_journal_line " +
                "WHERE company_id = ? AND (branch_id = ? OR branch_id IS NULL) AND supplier_id = ?";
        return count(sql, companyId, branchId, supplierId);
    }

    private long countOpenDocuments(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COUNT(*) FROM public.finance_posting_request " +
                "WHERE company_id = ? AND (branch_id = ? OR branch_id IS NULL) " +
                "AND request_payload ->> 'supplierId' = ? " +
                "AND status IN ('pending', 'processing', 'failed')";
        return count(sql, companyId, branchId, String.valueOf(supplierId));
    }

    private BigDecimal getSupplierOpenBalance(int companyId, int branchId, int supplierId) {
        String sql = "SELECT COALESCE(\"supplierRemainig\", 0)::numeric FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE \"supplierId\" = ?";
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, supplierId);
        return value == null ? BigDecimal.ZERO : value;
    }

    private long count(String sql, Object... params) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, params);
        return value == null ? 0L : value;
    }

    private Supplier getSupplierById(int supplierId, int branchId, int companyId) {
        ensureSupplierLifecycleColumns(companyId, branchId);
        String sql = "SELECT \"supplierId\", \"SupplierName\", \"supplierPhone1\", \"supplierPhone2\", " +
                "\"SupplierLocation\", \"suplierMajor\", \"supplierTotalSales\", \"supplierRemainig\", " +
                "\"supplierStatus\", \"archivedAt\"::text AS \"archivedAt\", \"archivedBy\", \"archiveReason\" " +
                "FROM " + TenantSqlIdentifiers.supplierTable(companyId, branchId) +
                " WHERE \"supplierId\" = ?";
        return jdbcTemplate.queryForObject(sql, SUPPLIER_ROW_MAPPER, supplierId);
    }

    private List<SupplierStatementLineResponse> getSupplierStatementLines(int supplierId,
                                                                          int branchId,
                                                                          int companyId,
                                                                          Timestamp fromTime,
                                                                          Timestamp toTime) {
        ArrayList<Object> params = new ArrayList<>();
        params.add(companyId);
        params.add(branchId);
        params.add(branchId);
        params.add(supplierId);
        params.add(companyId);
        params.add(branchId);
        params.add(branchId);
        params.add(supplierId);
        params.add(companyId);
        params.add(branchId);
        params.add(branchId);
        params.add(supplierId);

        String timeFilter = "";
        if (fromTime != null) {
            timeFilter += " AND activity_date >= ? ";
            params.add(fromTime);
        }
        if (toTime != null) {
            timeFilter += " AND activity_date < ? ";
            params.add(toTime);
        }

        String sql = """
                SELECT activity_date, source_type, source_id, source_number, description,
                       debit, credit, posting_status, posting_request_id, journal_id, posting_failure_reason
                FROM (
                    SELECT ledger.created_at AS activity_date,
                           CASE WHEN COALESCE(ledger.trans_total, 0) < 0 OR COALESCE(ledger.quantity_delta, 0) < 0
                                THEN 'purchase_return' ELSE 'purchase_invoice' END AS source_type,
                           ledger.stock_ledger_id::text AS source_id,
                           'INV-' || ledger.stock_ledger_id::text AS source_number,
                           COALESCE(ledger.note, ledger.movement_type, 'Inventory supplier transaction') AS description,
                           CASE WHEN COALESCE(ledger.trans_total, 0) < 0 OR COALESCE(ledger.quantity_delta, 0) < 0
                                THEN ABS(COALESCE(ledger.trans_total, 0))::numeric ELSE 0::numeric END AS debit,
                           CASE WHEN COALESCE(ledger.trans_total, 0) >= 0 AND COALESCE(ledger.quantity_delta, 0) >= 0
                                THEN COALESCE(ledger.trans_total, 0)::numeric ELSE 0::numeric END AS credit,
                           fp.status AS posting_status,
                           fp.posting_request_id,
                           fp.journal_entry_id AS journal_id,
                           fp.last_error AS posting_failure_reason
                    FROM %s ledger
                    LEFT JOIN public.finance_posting_request fp
                      ON fp.company_id = ?
                     AND fp.branch_id = ?
                     AND fp.source_module = 'purchase'
                     AND fp.source_id = 'inventory-transaction-' || ledger.stock_ledger_id::text
                    WHERE ledger.branch_id = ? AND COALESCE(ledger.supplier_id, 0) = ?
                    UNION ALL
                    SELECT receipt."receiptTime" AS activity_date,
                           'supplier_payment' AS source_type,
                           receipt."srId"::text AS source_id,
                           'SR-' || receipt."srId"::text AS source_number,
                           'Supplier payment' AS description,
                           receipt."amountPaid"::money::numeric AS debit,
                           0::numeric AS credit,
                           fp.status AS posting_status,
                           fp.posting_request_id,
                           fp.journal_entry_id AS journal_id,
                           fp.last_error AS posting_failure_reason
                    FROM %s receipt
                    LEFT JOIN public.finance_posting_request fp
                      ON fp.company_id = ?
                     AND fp.branch_id = ?
                     AND fp.source_module = 'payment'
                     AND fp.source_type = 'supplier_payment'
                     AND fp.source_id = 'supplier-receipt-' || receipt."srId"::text
                    WHERE receipt."branchId" = ? AND receipt."supplierId" = ?
                    UNION ALL
                    SELECT returned."time" AS activity_date,
                           'supplier_return' AS source_type,
                           returned."sBPId"::text AS source_id,
                           'SRT-' || returned."sBPId"::text AS source_number,
                           COALESCE(returned."desc", 'Return to supplier') AS description,
                           (COALESCE(returned.quantity, 0) * COALESCE(returned.cost, 0))::numeric AS debit,
                           0::numeric AS credit,
                           fp.status AS posting_status,
                           fp.posting_request_id,
                           fp.journal_entry_id AS journal_id,
                           fp.last_error AS posting_failure_reason
                    FROM %s returned
                    LEFT JOIN public.finance_posting_request fp
                      ON fp.company_id = ?
                     AND fp.branch_id = ?
                     AND fp.source_module = 'purchase'
                     AND fp.source_type = 'supplier_return'
                     AND fp.source_id = 'supplier-return-' || returned."sBPId"::text
                    WHERE returned."branchId" = ? AND returned."supplierId" = ?
                ) statement_activity
                WHERE 1 = 1
                %s
                ORDER BY activity_date ASC, source_type ASC, source_id ASC
                """.formatted(
                TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                TenantSqlIdentifiers.supplierReceiptsTable(companyId),
                TenantSqlIdentifiers.supplierBoughtProductTable(companyId),
                timeFilter
        );

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SupplierStatementLineResponse(
                        rs.getTimestamp("activity_date").toInstant(),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getString("source_number"),
                        rs.getString("description"),
                        zeroIfNull(rs.getBigDecimal("debit")),
                        zeroIfNull(rs.getBigDecimal("credit")),
                        BigDecimal.ZERO,
                        defaultPostingStatus(rs.getString("posting_status")),
                        nullableUuid(rs, "posting_request_id"),
                        nullableUuid(rs, "journal_id"),
                        rs.getString("posting_failure_reason")
                ),
                params.toArray()
        );
    }

    private List<SupplierOpenDocumentResponse> getOpenSupplierDocuments(int supplierId,
                                                                        int branchId,
                                                                        int companyId,
                                                                        LocalDate asOfDate) {
        String sql = "SELECT ledger.stock_ledger_id, ledger.created_at, COALESCE(ledger.remaining_amount, 0)::numeric AS open_amount " +
                "FROM " + TenantSqlIdentifiers.inventoryStockLedgerTable(companyId) + " ledger " +
                "WHERE ledger.branch_id = ? AND COALESCE(ledger.supplier_id, 0) = ? " +
                "AND ledger.created_at::date <= ? AND COALESCE(ledger.remaining_amount, 0) > 0 " +
                "ORDER BY ledger.created_at ASC, ledger.stock_ledger_id ASC";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Timestamp documentTime = rs.getTimestamp("created_at");
                    LocalDate documentDate = documentTime.toLocalDateTime().toLocalDate();
                    int ageDays = Math.max(0, (int) ChronoUnit.DAYS.between(documentDate, asOfDate));
                    String bucket = agingBucket(ageDays);
                    String sourceId = rs.getString("stock_ledger_id");
                    return new SupplierOpenDocumentResponse(
                            "purchase_invoice",
                            sourceId,
                            "INV-" + sourceId,
                            documentTime.toInstant(),
                            zeroIfNull(rs.getBigDecimal("open_amount")),
                            ageDays,
                            bucket
                    );
                },
                branchId,
                supplierId,
                Date.valueOf(asOfDate)
        );
    }

    private BigDecimal sumCreditMinusDebit(List<SupplierStatementLineResponse> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SupplierStatementLineResponse line : lines) {
            total = total.add(line.getCredit()).subtract(line.getDebit());
        }
        return total;
    }

    private BigDecimal sumDebit(List<SupplierStatementLineResponse> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SupplierStatementLineResponse line : lines) {
            total = total.add(line.getDebit());
        }
        return total;
    }

    private BigDecimal sumCredit(List<SupplierStatementLineResponse> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SupplierStatementLineResponse line : lines) {
            total = total.add(line.getCredit());
        }
        return total;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultPostingStatus(String value) {
        return value == null || value.isBlank() ? "not_required" : value;
    }

    private static UUID nullableUuid(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getObject(column, UUID.class);
    }

    private String agingBucket(int ageDays) {
        if (ageDays <= 0) {
            return "current";
        }
        if (ageDays <= 30) {
            return "days1To30";
        }
        if (ageDays <= 60) {
            return "days31To60";
        }
        if (ageDays <= 90) {
            return "days61To90";
        }
        return "over90";
    }

    private String normalizeSupplierNameKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeSupplierPhoneKey(String value) {
        return value == null ? "" : value.trim().replaceAll("[^0-9+]", "");
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
