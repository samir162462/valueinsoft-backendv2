package com.example.valueinsoftbackend.DatabaseRequests;

import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsReconModels;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stage 6 (OPEN_ITEMS_IMPLEMENTATION_ROADMAP.md): reconciliation and staging reads plus the
 * opening-import audit write. Query design follows OPEN_ITEMS_RECONCILIATION_PLAN.md §3.
 *
 * <p>Sign conventions: 1100 (AR) is debit-normal → balance = SUM(debit − credit);
 * 2100 (AP) is credit-normal → balance = SUM(credit − debit). Journal lines carry
 * source_module/source_type denormalized, so the platform-billing exclusion (review F9)
 * filters directly on the line without extra joins; posted-only is enforced through the
 * entry join.</p>
 */
@Repository
public class DbOpenItemsReconciliation {

    /** Tenant-billing source types that post to 1100 without a client (review F9). */
    public static final List<String> BILLING_SOURCE_TYPES = List.of(
            "billing_balance_settlement", "billing_balance_credit", "billing_payment_reversal");

    private final NamedParameterJdbcTemplate jdbc;

    public DbOpenItemsReconciliation(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Active tenant ids used by the opt-in Phase 8 reconciliation gauge refresh. */
    public List<Integer> companyIds() {
        return jdbc.queryForList("SELECT id FROM public.\"Company\" ORDER BY id",
                new MapSqlParameterSource(), Integer.class);
    }

    // ------------------------------------------------------------------
    // Subledger and control totals (§3.1 / §3.5)
    // ------------------------------------------------------------------

    public BigDecimal arSubledgerTotal(int companyId) {
        return zeroIfNull(jdbc.queryForObject(
                "SELECT COALESCE(SUM(remaining_amount),0) FROM " + TenantSqlIdentifiers.arOpenItemTable(companyId)
                        + " WHERE company_id=:companyId AND status IN ('OPEN','PARTIALLY_SETTLED')",
                new MapSqlParameterSource("companyId", companyId), BigDecimal.class));
    }

    public BigDecimal apSubledgerTotal(int companyId) {
        return zeroIfNull(jdbc.queryForObject(
                "SELECT COALESCE(SUM(remaining_amount),0) FROM " + TenantSqlIdentifiers.apOpenItemTable(companyId)
                        + " WHERE company_id=:companyId AND status IN ('OPEN','PARTIALLY_SETTLED')",
                new MapSqlParameterSource("companyId", companyId), BigDecimal.class));
    }

    /**
     * Posted balance of one control account (by account_code), split into the
     * client/supplier-attributable portion and the excluded platform-billing portion.
     * {@code debitNormal=true} for 1100, {@code false} for 2100.
     */
    public OpenItemsReconModels.ControlBalance controlBalance(int companyId, String accountCode,
                                                              boolean debitNormal) {
        String signed = debitNormal
                ? "l.debit_amount - l.credit_amount"
                : "l.credit_amount - l.debit_amount";
        return jdbc.queryForObject("""
                SELECT
                    COALESCE(SUM(%s), 0) AS total_balance,
                    COALESCE(SUM(%s) FILTER (
                        WHERE l.source_module = 'payment' AND l.source_type IN (:billingTypes)), 0) AS billing_portion
                  FROM public.finance_journal_line l
                  JOIN public.finance_journal_entry j
                    ON j.company_id = l.company_id AND j.journal_entry_id = l.journal_entry_id
                  JOIN public.finance_account a
                    ON a.company_id = l.company_id AND a.account_id = l.account_id
                 WHERE l.company_id = :companyId
                   AND a.account_code = :accountCode
                   AND j.status = 'posted'
                """.formatted(signed, signed),
                new MapSqlParameterSource("companyId", companyId)
                        .addValue("accountCode", accountCode)
                        .addValue("billingTypes", BILLING_SOURCE_TYPES),
                (rs, rowNum) -> {
                    BigDecimal total = rs.getBigDecimal("total_balance");
                    BigDecimal billing = rs.getBigDecimal("billing_portion");
                    return new OpenItemsReconModels.ControlBalance(accountCode, total, billing,
                            total.subtract(billing));
                });
    }

    // ------------------------------------------------------------------
    // AR staging (§3.2) — classification happens in the SERVICE via
    // PaymentTypeClassifier; SQL only aggregates per (client, orderType).
    // ------------------------------------------------------------------

    public List<Integer> companyBranchIds(int companyId) {
        return jdbc.queryForList(
                "SELECT \"branchId\" FROM public.\"Branch\" WHERE \"companyId\"=:companyId ORDER BY \"branchId\"",
                new MapSqlParameterSource("companyId", companyId), Integer.class);
    }

    /** Per (client, orderType) totals for one branch's order table. */
    public List<OpenItemsReconModels.ClientOrderTypeTotal> clientOrderTotals(int companyId, int branchId) {
        String table = TenantSqlIdentifiers.orderTable(companyId, branchId);
        return jdbc.query("""
                SELECT "clientId" AS client_id,
                       COALESCE("orderType",'') AS order_type,
                       COALESCE(SUM("orderTotal"),0)::numeric AS total,
                       BOOL_OR(COALESCE("orderBouncedBack",0) <> 0) AS has_bounce_backs
                  FROM %s
                 WHERE "clientId" IS NOT NULL AND "clientId" > 0
                 GROUP BY "clientId", COALESCE("orderType",'')
                """.formatted(table),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new OpenItemsReconModels.ClientOrderTypeTotal(
                        rs.getInt("client_id"), branchId, rs.getString("order_type"),
                        rs.getBigDecimal("total"), rs.getBoolean("has_bounce_backs")));
    }

    /** Positive/negative receipt sums per client across the tenant. */
    public List<OpenItemsReconModels.ClientReceiptTotals> clientReceiptTotals(int companyId) {
        return jdbc.query("""
                SELECT "clientId" AS client_id,
                       COALESCE(SUM(amount::numeric) FILTER (WHERE amount::numeric > 0), 0) AS paid_in,
                       COALESCE(SUM(amount::numeric) FILTER (WHERE amount::numeric < 0), 0) AS paid_out
                  FROM %s
                 GROUP BY "clientId"
                """.formatted(TenantSqlIdentifiers.clientReceiptsTable(companyId)),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new OpenItemsReconModels.ClientReceiptTotals(
                        rs.getInt("client_id"), rs.getBigDecimal("paid_in"), rs.getBigDecimal("paid_out")));
    }

    // ------------------------------------------------------------------
    // AP staging (§3.4): documents whose payment history is NOT fully explained
    // by linked supplier receipts. Rows returned here are import-blocked; rows
    // absent (delta = 0) qualify for document-level import (backfill option 4).
    // ------------------------------------------------------------------

    public List<OpenItemsReconModels.ApDocumentProof> apUnexplainedPurchases(int companyId) {
        return jdbc.query("""
                SELECT l.stock_ledger_id, l.branch_id, l.supplier_id,
                       COALESCE(l.trans_total,0)::numeric AS trans_total,
                       COALESCE(l.remaining_amount,0)::numeric AS remaining_amount,
                       COALESCE(r.receipts_total,0) AS receipts_total,
                       COALESCE(l.trans_total,0)::numeric - COALESCE(l.remaining_amount,0)::numeric
                         - COALESCE(r.receipts_total,0) AS unexplained_delta
                  FROM %s l
                  LEFT JOIN LATERAL (
                        SELECT SUM(sr."amountPaid"::numeric) AS receipts_total
                          FROM %s sr
                         WHERE sr."transId" = l.stock_ledger_id
                           AND COALESCE(sr.status,'POSTED') = 'POSTED'
                  ) r ON TRUE
                 WHERE l.movement_type = 'PURCHASE_RECEIPT'
                   AND COALESCE(l.supplier_id,0) > 0
                   AND (COALESCE(l.trans_total,0)::numeric - COALESCE(l.remaining_amount,0)::numeric
                        - COALESCE(r.receipts_total,0)) <> 0
                 ORDER BY l.stock_ledger_id
                """.formatted(TenantSqlIdentifiers.inventoryStockLedgerTable(companyId),
                        TenantSqlIdentifiers.supplierReceiptsTable(companyId)),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new OpenItemsReconModels.ApDocumentProof(
                        rs.getLong("stock_ledger_id"), rs.getInt("branch_id"), rs.getInt("supplier_id"),
                        rs.getBigDecimal("trans_total"), rs.getBigDecimal("remaining_amount"),
                        rs.getBigDecimal("receipts_total"), rs.getBigDecimal("unexplained_delta")));
    }

    /** Drift between ledger remaining and the mutable supplier running total (§3.3). */
    public List<OpenItemsReconModels.SupplierDrift> supplierDrift(int companyId, int branchId) {
        String ledger = TenantSqlIdentifiers.inventoryStockLedgerTable(companyId);
        String suppliers = TenantSqlIdentifiers.supplierTable(companyId, branchId);
        return jdbc.query("""
                SELECT s."supplierId" AS supplier_id,
                       COALESCE(l.ledger_remaining, 0) AS ledger_remaining,
                       COALESCE(s."supplierRemainig", 0)::numeric AS supplier_running_total
                  FROM %s s
                  LEFT JOIN LATERAL (
                        SELECT SUM(COALESCE(remaining_amount,0))::numeric AS ledger_remaining
                          FROM %s
                         WHERE supplier_id = s."supplierId" AND branch_id = :branchId
                           AND movement_type = 'PURCHASE_RECEIPT'
                  ) l ON TRUE
                 WHERE COALESCE(l.ledger_remaining, 0) <> COALESCE(s."supplierRemainig", 0)::numeric
                """.formatted(suppliers, ledger),
                new MapSqlParameterSource("branchId", branchId),
                (rs, rowNum) -> new OpenItemsReconModels.SupplierDrift(
                        rs.getInt("supplier_id"), branchId,
                        rs.getBigDecimal("ledger_remaining"), rs.getBigDecimal("supplier_running_total")));
    }

    // ------------------------------------------------------------------
    // Import support
    // ------------------------------------------------------------------

    public boolean openItemIdempotencyExists(int companyId, String side, String idempotencyKey) {
        String table = "AR".equals(side)
                ? TenantSqlIdentifiers.arOpenItemTable(companyId)
                : TenantSqlIdentifiers.apOpenItemTable(companyId);
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE company_id=:companyId AND idempotency_key=:key",
                new MapSqlParameterSource("companyId", companyId).addValue("key", idempotencyKey), Integer.class);
        return found != null && found > 0;
    }

    public long insertImportRun(int companyId, OpenItemsReconModels.ImportRunAudit audit) {
        return jdbc.queryForObject("INSERT INTO " + TenantSqlIdentifiers.openItemsOpeningImportRunTable(companyId)
                        + " (company_id, side, dry_run, status, parties_requested, parties_imported, parties_skipped,"
                        + " imported_total, subledger_total, control_attributable, variance, approved_variance,"
                        + " approver, notes, created_by) VALUES (:companyId, :side, :dryRun, :status, :requested,"
                        + " :imported, :skipped, :importedTotal, :subledgerTotal, :controlAttributable, :variance,"
                        + " :approvedVariance, :approver, :notes, :actor) RETURNING run_id",
                new MapSqlParameterSource("companyId", companyId)
                        .addValue("side", audit.side())
                        .addValue("dryRun", audit.dryRun())
                        .addValue("status", audit.status())
                        .addValue("requested", audit.partiesRequested())
                        .addValue("imported", audit.partiesImported())
                        .addValue("skipped", audit.partiesSkipped())
                        .addValue("importedTotal", audit.importedTotal())
                        .addValue("subledgerTotal", audit.subledgerTotal())
                        .addValue("controlAttributable", audit.controlAttributable())
                        .addValue("variance", audit.variance())
                        .addValue("approvedVariance", audit.approvedVariance())
                        .addValue("approver", audit.approver())
                        .addValue("notes", audit.notes())
                        .addValue("actor", audit.actor()), Long.class);
    }

    public List<OpenItemsReconModels.ImportRunAuditRow> importRuns(int companyId, String side, int limit) {
        return jdbc.query("SELECT run_id, side, dry_run, status, parties_requested, parties_imported,"
                        + " parties_skipped, imported_total, subledger_total, control_attributable, variance,"
                        + " approved_variance, approver, notes, created_by, created_at FROM "
                        + TenantSqlIdentifiers.openItemsOpeningImportRunTable(companyId)
                        + " WHERE company_id=:companyId AND (:side IS NULL OR side=:side)"
                        + " ORDER BY run_id DESC LIMIT :limit",
                new MapSqlParameterSource("companyId", companyId)
                        .addValue("side", side)
                        .addValue("limit", limit),
                (rs, rowNum) -> new OpenItemsReconModels.ImportRunAuditRow(
                        rs.getLong("run_id"), rs.getString("side"), rs.getBoolean("dry_run"),
                        rs.getString("status"), rs.getInt("parties_requested"), rs.getInt("parties_imported"),
                        rs.getInt("parties_skipped"), rs.getBigDecimal("imported_total"),
                        rs.getBigDecimal("subledger_total"), rs.getBigDecimal("control_attributable"),
                        rs.getBigDecimal("variance"), rs.getBigDecimal("approved_variance"),
                        rs.getString("approver"), rs.getString("notes"), rs.getString("created_by"),
                        rs.getTimestamp("created_at")));
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
