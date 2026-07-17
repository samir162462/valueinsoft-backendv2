package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReadModels;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DbApOpenItem {

    private final NamedParameterJdbcTemplate jdbc;

    public DbApOpenItem(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OpenItemsReadModels.OpenItemPage findOpenItems(int companyId, int branchId, int supplierId,
                                                           String status, LocalDate dueBefore,
                                                           int limit, int offset) {
        String table = TenantSqlIdentifiers.apOpenItemTable(companyId);
        StringBuilder where = new StringBuilder(
                " WHERE company_id=:companyId AND branch_id=:branchId AND supplier_id=:partyId");
        MapSqlParameterSource params = scope(companyId, branchId, supplierId)
                .addValue("limit", limit).addValue("offset", offset);
        if (status != null && !status.isBlank()) {
            where.append(" AND status=:status");
            params.addValue("status", status.trim().toUpperCase());
        }
        if (dueBefore != null) {
            where.append(" AND due_date < :dueBefore");
            params.addValue("dueBefore", Timestamp.valueOf(dueBefore.plusDays(1).atStartOfDay()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, params, Long.class);
        List<OpenItemsReadModels.OpenItem> items = jdbc.query(
                selectColumns(table) + where + " ORDER BY document_date DESC, open_item_id DESC LIMIT :limit OFFSET :offset",
                params, openItemMapper());
        return new OpenItemsReadModels.OpenItemPage(items, limit, offset, total == null ? 0 : total);
    }

    public OpenItemsReadModels.Statement getStatement(int companyId, int branchId, int supplierId,
                                                       LocalDate fromDate, LocalDate toDate) {
        MapSqlParameterSource params = scope(companyId, branchId, supplierId)
                .addValue("from", Timestamp.valueOf(fromDate.atStartOfDay()))
                .addValue("to", Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        String events = statementEvents(companyId);
        Map<String, BigDecimal> opening = balances(
                "WITH events AS (" + events + ") SELECT currency_code, COALESCE(SUM(amount),0) balance "
                        + "FROM events WHERE event_date < :from GROUP BY currency_code", params);
        Map<String, BigDecimal> running = new LinkedHashMap<>(opening);
        List<OpenItemsReadModels.StatementLine> lines = jdbc.query(
                "WITH events AS (" + events + ") SELECT * FROM events "
                        + "WHERE event_date >= :from AND event_date < :to ORDER BY event_date, source_id",
                params, (rs, rowNum) -> {
                    String currency = rs.getString("currency_code");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal balance = running.getOrDefault(currency, BigDecimal.ZERO).add(amount);
                    running.put(currency, balance);
                    return new OpenItemsReadModels.StatementLine(
                            rs.getTimestamp("event_date").toLocalDateTime(), rs.getString("event_type"),
                            rs.getLong("source_id"), rs.getString("document_ref"), currency, amount, balance);
                });
        return new OpenItemsReadModels.Statement(supplierId, branchId, fromDate, toDate,
                Map.copyOf(opening), lines, Map.copyOf(running));
    }

    public OpenItemsReadModels.Aging getAging(int companyId, int branchId, int supplierId, LocalDate asOfDate) {
        MapSqlParameterSource params = scope(companyId, branchId, supplierId).addValue("asOf", asOfDate);
        List<OpenItemsReadModels.AgingBucket> buckets = jdbc.query(agingSql(
                TenantSqlIdentifiers.apOpenItemTable(companyId)), params, agingMapper());
        return new OpenItemsReadModels.Aging(supplierId, branchId, asOfDate, buckets);
    }

    public String getCompanyCurrency(int companyId) {
        return jdbc.queryForObject("SELECT currency FROM public.\"Company\" WHERE id=:companyId",
                new MapSqlParameterSource("companyId", companyId), String.class);
    }

    public BigDecimal getPurchaseRemaining(int companyId, int branchId, int supplierId, long sourceId) {
        BigDecimal remaining = jdbc.queryForObject("SELECT remaining_amount FROM "
                        + TenantSqlIdentifiers.apOpenItemTable(companyId)
                        + " WHERE company_id=:companyId AND branch_id=:branchId AND supplier_id=:partyId "
                        + "AND source_type='PURCHASE' AND source_id=:sourceId",
                scope(companyId, branchId, supplierId).addValue("sourceId", sourceId), BigDecimal.class);
        return remaining == null ? BigDecimal.ZERO : remaining;
    }

    public int getSupplierPaymentTermsDays(int companyId, int branchId, int supplierId) {
        Integer days = jdbc.queryForObject("SELECT payment_terms_days FROM "
                        + TenantSqlIdentifiers.supplierTable(companyId, branchId) + " WHERE \"supplierId\"=:supplierId",
                new MapSqlParameterSource("supplierId", supplierId), Integer.class);
        return days == null ? 0 : days;
    }

    public List<OpenItemsReadModels.OpenItem> findOpenItemsForUpdate(int companyId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String table = TenantSqlIdentifiers.apOpenItemTable(companyId);
        return jdbc.query(selectColumns(table)
                        + " WHERE company_id=:companyId AND open_item_id IN (:ids) ORDER BY open_item_id FOR UPDATE",
                new MapSqlParameterSource("companyId", companyId).addValue("ids", ids), openItemMapper());
    }

    public OpenItemsReadModels.ReceiptLock findReceiptForUpdate(int companyId, int receiptId) {
        return jdbc.queryForObject("SELECT \"srId\", \"branchId\", \"supplierId\", \"amountPaid\"::numeric amount, status FROM "
                        + TenantSqlIdentifiers.supplierReceiptsTable(companyId) + " WHERE \"srId\"=:receiptId FOR UPDATE",
                new MapSqlParameterSource("receiptId", receiptId),
                (rs, rowNum) -> new OpenItemsReadModels.ReceiptLock(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                        rs.getBigDecimal(4), rs.getString(5)));
    }

    public OpenItemsWriteModels.NoteLock findDebitNoteForUpdate(int companyId, long noteId) {
        return jdbc.queryForObject("SELECT debit_note_id, branch_id, supplier_id, currency_code, total_amount, "
                        + "applied_amount, unapplied_amount, status FROM " + TenantSqlIdentifiers.apDebitNoteTable(companyId)
                        + " WHERE company_id=:companyId AND debit_note_id=:noteId FOR UPDATE",
                new MapSqlParameterSource("companyId", companyId).addValue("noteId", noteId), noteLockMapper());
    }

    public List<OpenItemsReadModels.OpenItem> findSettleableForUpdate(int companyId, int branchId, int supplierId,
                                                                      String currencyCode) {
        String table = TenantSqlIdentifiers.apOpenItemTable(companyId);
        return jdbc.query(selectColumns(table) + " WHERE company_id=:companyId AND branch_id=:branchId "
                        + "AND supplier_id=:partyId AND UPPER(currency_code)=UPPER(:currency) "
                        + "AND status IN ('OPEN','PARTIALLY_SETTLED') "
                        + "ORDER BY due_date NULLS LAST, document_date, open_item_id FOR UPDATE",
                scope(companyId, branchId, supplierId).addValue("currency", currencyCode), openItemMapper());
    }

    public List<OpenItemsWriteModels.AllocationRow> findAllocationsByPrefix(int companyId, String prefix) {
        return jdbc.query("SELECT allocation_id, receipt_id, debit_note_id note_id, open_item_id, branch_id, supplier_id party_id, "
                        + "currency_code, amount, status, reversal_of_allocation_id, idempotency_key FROM "
                        + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " WHERE company_id=:companyId AND idempotency_key LIKE :prefix ORDER BY allocation_id",
                new MapSqlParameterSource("companyId", companyId).addValue("prefix", prefix + "%"), allocationMapper());
    }

    public long insertAllocation(int companyId, int branchId, int supplierId, Integer receiptId, Long debitNoteId,
                                 long openItemId, BigDecimal amount, String currencyCode,
                                 String idempotencyKey, String actor) {
        return jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " (company_id,branch_id,supplier_id,receipt_id,debit_note_id,open_item_id,amount,currency_code,idempotency_key,created_by) "
                        + "VALUES (:companyId,:branchId,:partyId,:receiptId,:noteId,:openItemId,:amount,:currency,:key,:actor) RETURNING allocation_id",
                scope(companyId, branchId, supplierId).addValue("receiptId", receiptId).addValue("noteId", debitNoteId)
                        .addValue("openItemId", openItemId).addValue("amount", amount).addValue("currency", currencyCode)
                        .addValue("key", idempotencyKey).addValue("actor", actor), Long.class);
    }

    public void updateSettlement(int companyId, long openItemId, BigDecimal settled, BigDecimal remaining,
                                 String status, String actor) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.apOpenItemTable(companyId)
                        + " SET settled_amount=:settled, remaining_amount=:remaining, status=:status, updated_by=:actor, "
                        + "updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE open_item_id=:id",
                new MapSqlParameterSource("settled", settled).addValue("remaining", remaining)
                        .addValue("status", status).addValue("actor", actor).addValue("id", openItemId));
    }

    public void updateDebitNoteApplication(int companyId, long noteId, BigDecimal applied, BigDecimal unapplied,
                                           String status, String actor) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.apDebitNoteTable(companyId)
                        + " SET applied_amount=:applied, unapplied_amount=:unapplied, status=:status, updated_by=:actor, "
                        + "updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE debit_note_id=:id",
                new MapSqlParameterSource("applied", applied).addValue("unapplied", unapplied)
                        .addValue("status", status).addValue("actor", actor).addValue("id", noteId));
    }

    public OpenItemsWriteModels.AllocationRow findAllocationForUpdate(int companyId, long allocationId) {
        return jdbc.queryForObject("SELECT allocation_id, receipt_id, debit_note_id note_id, open_item_id, branch_id, supplier_id party_id, "
                        + "currency_code, amount, status, reversal_of_allocation_id, idempotency_key FROM "
                        + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " WHERE company_id=:companyId AND allocation_id=:id FOR UPDATE",
                new MapSqlParameterSource("companyId", companyId).addValue("id", allocationId), allocationMapper());
    }

    public int countActiveAllocationsForReceipt(int companyId, int receiptId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " WHERE receipt_id=:id AND status='POSTED' AND reversal_of_allocation_id IS NULL",
                new MapSqlParameterSource("id", receiptId), Integer.class);
        return count == null ? 0 : count;
    }

    public BigDecimal sumActiveAllocationsForReceipt(int companyId, int receiptId) {
        BigDecimal amount = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM "
                        + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " WHERE receipt_id=:id AND status='POSTED' AND reversal_of_allocation_id IS NULL",
                new MapSqlParameterSource("id", receiptId), BigDecimal.class);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public long insertAllocationReversal(int companyId, OpenItemsWriteModels.AllocationRow original,
                                         String idempotencyKey, String actor) {
        return jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " (company_id,branch_id,supplier_id,receipt_id,debit_note_id,open_item_id,amount,currency_code,"
                        + "idempotency_key,reversal_of_allocation_id,created_by) VALUES (:companyId,:branchId,:partyId,"
                        + ":receiptId,:noteId,:openItemId,:amount,:currency,:key,:originalId,:actor) RETURNING allocation_id",
                new MapSqlParameterSource("companyId", companyId).addValue("branchId", original.branchId())
                        .addValue("partyId", original.partyId()).addValue("receiptId", original.receiptId())
                        .addValue("noteId", original.noteId()).addValue("openItemId", original.openItemId())
                        .addValue("amount", original.amount()).addValue("currency", original.currencyCode())
                        .addValue("key", idempotencyKey).addValue("originalId", original.allocationId())
                        .addValue("actor", actor), Long.class);
    }

    public void markAllocationReversed(int companyId, long allocationId) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.apPaymentAllocationTable(companyId)
                        + " SET status='REVERSED' WHERE allocation_id=:id", new MapSqlParameterSource("id", allocationId));
    }

    public void markReceiptReversed(int companyId, int receiptId) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.supplierReceiptsTable(companyId)
                        + " SET status='REVERSED' WHERE \"srId\"=:id AND status='POSTED'",
                new MapSqlParameterSource("id", receiptId));
    }

    public void reverseOpenItem(int companyId, long openItemId, String actor) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.apOpenItemTable(companyId)
                        + " SET settled_amount=total_amount, remaining_amount=0, status='REVERSED', updated_by=:actor, "
                        + "updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE open_item_id=:id",
                new MapSqlParameterSource("actor", actor).addValue("id", openItemId));
    }

    public long createOpenItem(int companyId, int branchId, int supplierId, String sourceType, Long sourceId,
                               String documentRef, LocalDateTime documentDate, LocalDateTime dueDate,
                               String currencyCode, BigDecimal total, String idempotencyKey, String actor) {
        return jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.apOpenItemTable(companyId)
                        + " (company_id,branch_id,supplier_id,source_type,source_id,document_ref,document_date,due_date,"
                        + "currency_code,total_amount,remaining_amount,idempotency_key,created_by) VALUES (:companyId,"
                        + ":branchId,:partyId,:sourceType,:sourceId,:documentRef,:documentDate,:dueDate,:currency,:total,"
                        + ":total,:key,:actor) ON CONFLICT (company_id,branch_id,source_type,source_id) WHERE source_id IS NOT NULL "
                        + "DO UPDATE SET source_id=EXCLUDED.source_id RETURNING open_item_id",
                scope(companyId, branchId, supplierId).addValue("sourceType", sourceType).addValue("sourceId", sourceId)
                        .addValue("documentRef", documentRef).addValue("documentDate", Timestamp.valueOf(documentDate))
                        .addValue("dueDate", dueDate == null ? null : Timestamp.valueOf(dueDate)).addValue("currency", currencyCode)
                        .addValue("total", total).addValue("key", idempotencyKey).addValue("actor", actor), Long.class);
    }

    public OpenItemsWriteModels.NoteResult findDebitNoteByIdempotency(int companyId, String idempotencyKey) {
        List<OpenItemsWriteModels.NoteResult> rows = jdbc.query("SELECT debit_note_id,branch_id,supplier_id,currency_code,"
                        + "total_amount,applied_amount,unapplied_amount,status FROM "
                        + TenantSqlIdentifiers.apDebitNoteTable(companyId)
                        + " WHERE company_id=:companyId AND idempotency_key=:key",
                new MapSqlParameterSource("companyId", companyId).addValue("key", idempotencyKey), noteResultMapper(true));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long createDebitNote(int companyId, OpenItemsWriteModels.NoteCreateCommand command, String actor) {
        return jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.apDebitNoteTable(companyId)
                        + " (company_id,branch_id,supplier_id,reason,reference_type,reference_id,currency_code,total_amount,"
                        + "unapplied_amount,idempotency_key,notes,created_by) VALUES (:companyId,:branchId,:partyId,:reason,"
                        + ":referenceType,:referenceId,:currency,:total,:total,:key,:notes,:actor) RETURNING debit_note_id",
                scope(companyId, command.branchId(), command.partyId()).addValue("reason", command.reason())
                        .addValue("referenceType", command.referenceType()).addValue("referenceId", command.referenceId())
                        .addValue("currency", command.currencyCode()).addValue("total", command.totalAmount())
                        .addValue("key", command.idempotencyKey()).addValue("notes", command.notes()).addValue("actor", actor), Long.class);
    }

    public void markDebitNoteReversed(int companyId, long noteId, String actor) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.apDebitNoteTable(companyId)
                        + " SET applied_amount=total_amount,unapplied_amount=0,status='REVERSED',updated_by=:actor,"
                        + "updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE debit_note_id=:id",
                new MapSqlParameterSource("actor", actor).addValue("id", noteId));
    }

    public void setDebitNotePostingReferences(int companyId, long noteId, java.util.UUID postingRequestId,
                                              java.util.UUID journalEntryId) {
        jdbc.update("UPDATE " + TenantSqlIdentifiers.apDebitNoteTable(companyId)
                        + " SET posting_request_id=:requestId,journal_entry_id=:journalId WHERE debit_note_id=:id",
                new MapSqlParameterSource("requestId", postingRequestId).addValue("journalId", journalEntryId)
                        .addValue("id", noteId));
    }

    private static RowMapper<OpenItemsWriteModels.AllocationRow> allocationMapper() {
        return (rs, rowNum) -> new OpenItemsWriteModels.AllocationRow(rs.getLong("allocation_id"),
                (Integer) rs.getObject("receipt_id"), (Long) rs.getObject("note_id", Long.class),
                rs.getLong("open_item_id"), rs.getInt("branch_id"), rs.getInt("party_id"),
                rs.getString("currency_code"), rs.getBigDecimal("amount"), rs.getString("status"),
                (Long) rs.getObject("reversal_of_allocation_id", Long.class), rs.getString("idempotency_key"));
    }

    private static RowMapper<OpenItemsWriteModels.NoteLock> noteLockMapper() {
        return (rs, rowNum) -> new OpenItemsWriteModels.NoteLock(rs.getLong(1), rs.getInt(2), rs.getInt(3),
                rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8));
    }

    private static RowMapper<OpenItemsWriteModels.NoteResult> noteResultMapper(boolean replay) {
        return (rs, rowNum) -> new OpenItemsWriteModels.NoteResult(rs.getLong(1), rs.getInt(2), rs.getInt(3),
                rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8), replay);
    }

    private String statementEvents(int companyId) {
        String oi = TenantSqlIdentifiers.apOpenItemTable(companyId);
        String alloc = TenantSqlIdentifiers.apPaymentAllocationTable(companyId);
        return "SELECT document_date event_date, 'OPEN_ITEM' event_type, open_item_id source_id, "
                + "document_ref, currency_code, total_amount amount FROM " + oi
                + " WHERE company_id=:companyId AND branch_id=:branchId AND supplier_id=:partyId AND status <> 'REVERSED' "
                + "UNION ALL SELECT a.created_at, CASE WHEN a.reversal_of_allocation_id IS NULL THEN 'ALLOCATION' "
                + "ELSE 'ALLOCATION_REVERSAL' END, a.allocation_id, oi.document_ref, a.currency_code, "
                + "CASE WHEN a.reversal_of_allocation_id IS NULL THEN -a.amount ELSE a.amount END "
                + "FROM " + alloc + " a JOIN " + oi + " oi ON oi.open_item_id=a.open_item_id "
                + "WHERE a.company_id=:companyId AND a.branch_id=:branchId AND a.supplier_id=:partyId";
    }

    private Map<String, BigDecimal> balances(String sql, MapSqlParameterSource params) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query(sql, params, (RowCallbackHandler) rs ->
                result.put(rs.getString("currency_code"), rs.getBigDecimal("balance")));
        return result;
    }

    private static String selectColumns(String table) {
        return "SELECT open_item_id, company_id, branch_id, supplier_id party_id, source_type, source_id, "
                + "document_ref, document_date, due_date, currency_code, total_amount, settled_amount, "
                + "remaining_amount, status, notes FROM " + table;
    }

    private static String agingSql(String table) {
        return "SELECT currency_code, "
                + "COALESCE(SUM(remaining_amount) FILTER (WHERE due_date IS NULL OR due_date::date >= :asOf),0), "
                + "COALESCE(SUM(remaining_amount) FILTER (WHERE :asOf - due_date::date BETWEEN 1 AND 30),0), "
                + "COALESCE(SUM(remaining_amount) FILTER (WHERE :asOf - due_date::date BETWEEN 31 AND 60),0), "
                + "COALESCE(SUM(remaining_amount) FILTER (WHERE :asOf - due_date::date BETWEEN 61 AND 90),0), "
                + "COALESCE(SUM(remaining_amount) FILTER (WHERE :asOf - due_date::date > 90),0), "
                + "COALESCE(SUM(remaining_amount),0) FROM " + table
                + " WHERE company_id=:companyId AND branch_id=:branchId AND supplier_id=:partyId "
                + "AND status IN ('OPEN','PARTIALLY_SETTLED') AND document_date::date <= :asOf GROUP BY currency_code";
    }

    private static RowMapper<OpenItemsReadModels.AgingBucket> agingMapper() {
        return (rs, rowNum) -> new OpenItemsReadModels.AgingBucket(rs.getString(1), rs.getBigDecimal(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7));
    }

    private static RowMapper<OpenItemsReadModels.OpenItem> openItemMapper() {
        return (rs, rowNum) -> new OpenItemsReadModels.OpenItem(
                rs.getLong("open_item_id"), rs.getInt("company_id"), rs.getInt("branch_id"), rs.getInt("party_id"),
                rs.getString("source_type"), (Long) rs.getObject("source_id", Long.class), rs.getString("document_ref"),
                rs.getTimestamp("document_date").toLocalDateTime(),
                rs.getTimestamp("due_date") == null ? null : rs.getTimestamp("due_date").toLocalDateTime(),
                rs.getString("currency_code"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("settled_amount"),
                rs.getBigDecimal("remaining_amount"), rs.getString("status"), rs.getString("notes"));
    }

    private static MapSqlParameterSource scope(int companyId, int branchId, int partyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(partyId, "supplierId");
        return new MapSqlParameterSource("companyId", companyId)
                .addValue("branchId", branchId).addValue("partyId", partyId);
    }
}
